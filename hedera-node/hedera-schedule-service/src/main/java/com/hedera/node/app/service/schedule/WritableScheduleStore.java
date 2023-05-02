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

import com.hedera.hapi.node.base.ScheduleID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public interface WritableScheduleStore extends ReadableScheduleStore {

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from this state as of the
     * consensus time {@link Instant} provided.
     * @param scheduleToDelete The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @throws IllegalStateException if the {@link ScheduleID} to be deleted is not present in this state,
     *     or the ID value has a mismatched realm or shard for this node.
     */
    public void delete(@Nullable final ScheduleID scheduleToDelete, @NonNull final Instant consensusTime);

    // Should return ScheduleVirtualValue, but we can't see that class, or any of it's implemented interfaces.
    // @todo('4211') Change this to return an appropriate object defined here, in PBJ, or in the SPI module
    //               (and matching ScheduleVirtualValue content)
    public void getForModify(final ScheduleID idToFind);

}
