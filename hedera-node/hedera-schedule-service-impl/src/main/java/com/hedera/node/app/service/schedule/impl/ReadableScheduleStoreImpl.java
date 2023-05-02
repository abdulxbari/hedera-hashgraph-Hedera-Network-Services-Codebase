/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules. If the scheduleID is valid and a schedule exists returns {@link
 * ScheduleVirtualValue}.
 *
 */
public class ReadableScheduleStoreImpl implements ReadableScheduleStore {
    private static final String NULL_STATE_IN_CONSTRUCTOR_MESSAGE =
            "Null states instance passed to ReadableScheduleStore constructor, possible state corruption.";
    private static final int ED_25519_LENGTH = 32;
    private static final int ECDSA_SECP256K1_LENGTH = 33;

    /** The underlying data storage class that holds the token data. */
    private final ReadableKVState<Long, ScheduleVirtualValue> schedulesById;

    /**
     * Create a new {@link ReadableScheduleStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableScheduleStoreImpl(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states, NULL_STATE_IN_CONSTRUCTOR_MESSAGE);
        schedulesById = states.get("SCHEDULES_BY_ID");
    }

    @Override
    @NonNull
    public Optional<ScheduleMetadata> get(@Nullable final ScheduleID id) {
        final ScheduleVirtualValue schedule = (id == null) ? null : schedulesById.get(id.scheduleNum());
        if (schedule != null) {
            final RichInstant calculatedExpiry = schedule.calculatedExpirationTime();
            final Instant expirationInstant = calculatedExpiry != null ? calculatedExpiry.toJava() : Instant.MAX;
            final Set<Key> signatories = new HashSet<>(schedule.signatories().size());
            addAsKeys(signatories, schedule.signatories());
            return Optional.of(new ScheduleMetadata(
                    schedule.adminKey().isPresent() ? PbjConverter.asPbjKey(schedule.adminKey().get()) : null,
                    PbjConverter.toPbj(schedule.ordinaryViewOfScheduledTxn()),
                    schedule.hasExplicitPayer() ? Optional.of(schedule.payer().toPbjAccountId()) : Optional.empty(),
                    schedule.schedulingAccount().toPbjAccountId(), schedule.isExecuted(), schedule.isDeleted(),
                    expirationInstant, signatories));
        } else {
            return Optional.empty();
        }
    }

    private void addAsKeys(@NonNull final Set<Key> metadataSignatories, @NonNull final List<byte[]> stateSignatories) {
        final Key.Builder keyBuilder = Key.newBuilder();
        for(final byte[] nextKey : stateSignatories) {
            final Bytes nextBytes = Bytes.wrap(nextKey);
            if (nextKey.length == ED_25519_LENGTH)
                keyBuilder.ed25519(nextBytes);
            else if(nextKey.length == ECDSA_SECP256K1_LENGTH)
                keyBuilder.ecdsaSecp256k1(nextBytes);
            else
                throw new IllegalStateException("Invalid key of length %d encountered".formatted(nextKey.length));
            metadataSignatories.add(keyBuilder.build());
        }
    }

}
