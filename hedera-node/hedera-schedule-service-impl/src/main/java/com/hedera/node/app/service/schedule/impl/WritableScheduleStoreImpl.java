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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

/**
 *
 */
public class WritableScheduleStoreImpl extends ReadableScheduleStoreImpl implements WritableScheduleStore {
    private static final String SCHEDULE_MISSING_FOR_DELETE_MESSAGE =
            "Schedule to be deleted, %1$,d, not found in state.";
    private static final String SCHEDULE_WRONG_SHARD_OR_REALM =
            "Schedule to be deleted, %1$d.%2$d.%3$d has an invalid shard or realm ID.";
    /**
     *  A Writable state store of schedules by long schedule ID.
     *  Ideally this would be keyed to a ScheduleID, but for now we need to work with legacy
     *  code that still assumes entity numbers, rather than full ID values with realm and shard.
     */
    private final WritableKVState<Long, ScheduleVirtualValue> schedulesById;

    /**
     * Create a new {@link WritableScheduleStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public WritableScheduleStoreImpl(@NonNull final WritableStates states) {
        super(states);
        schedulesById = states.get(ScheduleServiceImpl.SCHEDULES_BY_ID_KEY);
    }

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from this state as of the
     * consensus time {@link Instant} provided.
     * @param scheduleToDelete The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @throws IllegalStateException if the {@link ScheduleID} to be deleted is not present in this state,
     *     or the ID value has a mismatched realm or shard for this node.
     */
    public void delete(@Nullable final ScheduleID scheduleToDelete, @NonNull final Instant consensusTime) {
        Objects.requireNonNull(consensusTime, "Null consensusTime provided to schedule delete, cannot proceed.");
        if (scheduleToDelete != null) {
            final Long scheduleNumber = getScheduleNumberIfValidReamAndShard(scheduleToDelete);
            if (scheduleNumber != null) {
                final ScheduleVirtualValue schedule = schedulesById.getForModify(scheduleNumber);
                if (schedule != null) {
                    schedule.markDeleted(consensusTime);
                } else {
                    throw new IllegalStateException(SCHEDULE_MISSING_FOR_DELETE_MESSAGE.formatted(scheduleNumber));
                }
            } else {
                throw new IllegalStateException(SCHEDULE_WRONG_SHARD_OR_REALM.formatted(
                        scheduleToDelete.shardNum(), scheduleToDelete.realmNum(), scheduleToDelete.scheduleNum()));
            }
        }
    }

    @SuppressWarnings("AccessStaticViaInstance")
    @Nullable
    private Long getScheduleNumberIfValidReamAndShard(@NonNull final ScheduleID scheduleToDelete) {
        if (StaticPropertiesHolder.STATIC_PROPERTIES.getShard() == scheduleToDelete.shardNum()
                && StaticPropertiesHolder.STATIC_PROPERTIES.getRealm() == scheduleToDelete.realmNum()) {
            return Long.valueOf(scheduleToDelete.scheduleNum());
        } else {
            return null;
        }
    }

    @Override
    public void getForModify(@Nullable final ScheduleID idToFind) {
    }

    // This is garbage forced by the fact that Schedule does not exist in PBJ yet.
    // It has to be created in the protobuf repo by someone with access, then getForModify updated
    // in the interface and implemented above.
    // Note that getForModify may need to optionally convert from ScheduleVirtualValue to PBJ Schedule
    //      in order to support migration from old to new state representation
    public ScheduleVirtualValue getVirtualValueForModify(@Nullable final ScheduleID idToFind) {
        final ScheduleVirtualValue result;
        if (idToFind != null) {
            final Long scheduleNumber = getScheduleNumberIfValidReamAndShard(idToFind);
            if (scheduleNumber != null) {
                result = schedulesById.getForModify(scheduleNumber);
            } else {
                result = null; // @todo('merge') Not sure if this is correct, need to research a bit more.
            }
        } else {
            result = null; // @todo('merge') Not sure if this is correct, need to research a bit more.
        }
        return result;
    }

}
