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

package com.hedera.node.app.info;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.info.NodeInfo;
import com.swirlds.common.system.InitTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link NodeInfo} that delegates to the mono-service.
 */
public class MonoNodeInfo implements NodeInfo {

    private final com.hedera.node.app.service.mono.context.NodeInfo delegate;
    private final InitTrigger initTrigger;

    /**
     * Constructs a {@link MonoNodeInfo} with the given delegate.
     *
     * @param delegate the delegate
     * @param initTrigger the init trigger for access to node starting state
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MonoNodeInfo(
            @NonNull com.hedera.node.app.service.mono.context.NodeInfo delegate,
            @NonNull final InitTrigger initTrigger) {
        this.delegate = requireNonNull(delegate);
        this.initTrigger = initTrigger;
    }

    @Override
    public boolean isSelfZeroStake() {
        return delegate.isSelfZeroStake();
    }

    @Override
    public AccountID accountOf(final long nodeId) {
        return PbjConverter.toPbj(delegate.accountOf(nodeId));
    }

    //    /**
    //     * Convenience method to get this node's account number from the address book.
    //     *
    //     * @return this node's account number from the address book.
    //     */
    //    @Override
    //    public long accountNum() {
    //        return delegate.selfAccount().getAccountNum();
    //    }

    /**
     * Convenience method to get the memo of this node's account which is in the address book.
     *
     * @return this node's account memo
     */
    @Override
    public String accountMemo() {
        return "TEMP ACCOUNT MEMO"; // TODO where to get this from and what happens if it changes
    }

    /**
     * True if the node was initialized in event stream recovery state.
     *
     * @return if the node was initialized in event stream recovery state.
     */
    @Override
    public boolean wasStartedInEventStreamRecovery() {
        return initTrigger == InitTrigger.EVENT_STREAM_RECOVERY;
    }

    /**
     * The version of HAPI API that this node is running.
     *
     * @return HAPI API version
     */
    @Override
    public SemanticVersion hapiVersion() {
        // TODO where should this come from?
        return new SemanticVersion(0, 38, 0, null, null);
    }
}
