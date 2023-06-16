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

package com.hedera.node.app.spi.info;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Summarizes useful information about the nodes in the {@link AddressBook} from the Platform. In
 * the future, there may be events that require re-reading the book; but at present nodes may treat
 * the initializing book as static.
 */
public interface NodeInfo {

    /**
     * Convenience method to check if this node is zero-stake.
     *
     * @return whether this node has zero stake.
     */
    boolean isSelfZeroStake();

    /**
     * Returns the account parsed from the address book memo corresponding to the given node id.
     *
     * @param nodeId the id of interest
     * @return the account parsed from the address book memo corresponding to the given node id.
     * @throws IllegalArgumentException if the book did not contain the id, or was missing an
     *     account for the id
     */
    @NonNull
    AccountID accountOf(long nodeId);

    /**
     * Returns if the given node id is valid and the address book contains the id.
     * @param nodeId the id of interest
     * @return true if the given node id is valid. False otherwise.
     */
    default boolean isValidId(long nodeId) {
        try {
            accountOf(nodeId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Convenience method to get the memo of this node's account which is in the address book.
     *
     * @return this node's account memo
     */
    String accountMemo();

    /**
     * True if the node was initialized in event stream recovery state.
     *
     * @return if the node was initialized in event stream recovery state.
     */
    boolean wasStartedInEventStreamRecovery();

    /**
     * The version of HAPI API that this node is running.
     *
     * @return HAPI API version
     */
    SemanticVersion hapiVersion();
}
