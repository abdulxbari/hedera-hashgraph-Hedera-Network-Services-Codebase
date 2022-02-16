package com.hedera.services.utils.accessors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.swirlds.common.SwirldTransaction;

public class TopicCreateAccessor extends PlatformTxnAccessor {
	final ConsensusCreateTopicTransactionBody body;

	public TopicCreateAccessor(final SwirldTransaction txn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(txn, aliasManager);
		this.body = getTxn().getConsensusCreateTopic();
	}

	public Id accountToAutoRenew() {
		return unaliased(body.getAutoRenewAccount()).toId();
	}
}
