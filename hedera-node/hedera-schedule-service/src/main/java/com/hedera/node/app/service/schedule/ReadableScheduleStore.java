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

package com.hedera.node.app.service.schedule;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules. If the scheduleID is valid and a schedule exists returns {@link
 * ScheduleMetadata}.
 */
public interface ReadableScheduleStore {

    /**
     * Gets the schedule with the given {@link ScheduleID}. If there is no schedule with given ID
     * returns {@link Optional#empty()}.
     *
     * @param id given id for the schedule
     * @return the schedule with the given id
     */
    @NonNull
    Optional<ScheduleMetadata> get(@Nullable ScheduleID id);

    /**
     * Metadata about a schedule.
    private final List<byte[]> signatories = new ArrayList<>();
    private final Set<ByteString> notary = ConcurrentHashMap.newKeySet();
     *
     * @param adminKey admin key on the schedule
     * @param scheduledTxn scheduled transaction
     * @param designatedPayer payer for the schedule execution.If there is no explicit payer,
     *     returns {@link Optional#empty()}.
     */
    public record ScheduleMetadata(
            Key adminKey, TransactionBody scheduledTxn, Optional<AccountID> designatedPayer,
            AccountID schedulingAccount, boolean scheduleExecuted, boolean scheduleDeleted,
            Instant calculatedExpirationTime, Set<Key> signatories) {}
}
