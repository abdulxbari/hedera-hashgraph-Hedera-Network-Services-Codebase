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
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.SwirldTransaction;

/**
 * Encapsulates access to several commonly referenced parts of a {@link com.swirlds.common.SwirldTransaction}
 * whose contents is <i>supposed</i> to be a Hedera Services gRPC {@link Transaction}. (The constructor of this
 * class immediately tries to parse the {@code byte[]} contents of the txn, and propagates any protobuf
 * exceptions encountered.)
 */
public class PlatformTxnAccessor extends SignedTxnAccessor {
	private final SwirldTransaction platformTxn;
	private final AliasManager aliasManager;

	private RationalizedSigMeta sigMeta = null;

	public PlatformTxnAccessor(SwirldTransaction platformTxn, final AliasManager aliasManager)
			throws InvalidProtocolBufferException {
		super(platformTxn.getContents());
		this.platformTxn = platformTxn;
		this.aliasManager = aliasManager;
	}

	/**
	 * Convenience static factory for a txn whose {@code byte[]} contents are <i>certain</i>
	 * to be a valid serialized gRPC txn.
	 *
	 * @param platformTxn
	 * 		the txn to provide accessors for.
	 * @param aliasManager
	 * @return an initialized accessor.
	 */

	public static PlatformTxnAccessor uncheckedAccessorFor(SwirldTransaction platformTxn,
			final AliasManager aliasManager) {
		try {
			return new PlatformTxnAccessor(platformTxn, aliasManager);
		} catch (InvalidProtocolBufferException ignore) {
			throw new IllegalStateException("Unchecked accessor construction must get valid gRPC bytes!");
		}
	}

	@Override
	public SwirldTransaction getPlatformTxn() {
		return platformTxn;
	}

	@Override
	public void setSigMeta(RationalizedSigMeta sigMeta) {
		this.sigMeta = sigMeta;
	}

	@Override
	public RationalizedSigMeta getSigMeta() {
		return sigMeta;
	}

	protected EntityNum unaliased(AccountID grpcId) {
		return aliasManager.unaliased(grpcId);
	}

	protected EntityNum unaliased(ContractID grpcId) {
		return EntityIdUtils.unaliased(grpcId, aliasManager);
	}
}
