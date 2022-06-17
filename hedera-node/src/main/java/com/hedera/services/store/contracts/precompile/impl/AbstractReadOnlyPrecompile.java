package com.hedera.services.store.contracts.precompile.impl;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
 * ‍
 */

import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfoProvider;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.function.UnaryOperator;

public abstract class AbstractReadOnlyPrecompile implements Precompile {
	protected TokenID tokenId;
	protected final SyntheticTxnFactory syntheticTxnFactory;
	protected final WorldLedgers ledgers;
	protected final EncodingFacade encoder;
	protected final DecodingFacade decoder;
	protected final PrecompilePricingUtils pricingUtils;

	protected AbstractReadOnlyPrecompile(
			final TokenID tokenId,
			final SyntheticTxnFactory syntheticTxnFactory,
			final WorldLedgers ledgers,
			final EncodingFacade encoder,
			final DecodingFacade decoder,
			final PrecompilePricingUtils pricingUtils
	) {
		this.tokenId = tokenId;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.ledgers = ledgers;
		this.encoder = encoder;
		this.decoder = decoder;
		this.pricingUtils = pricingUtils;
	}

	@Override
	public TransactionBody.Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
		return syntheticTxnFactory.createTransactionCall(1L, input);
	}

	@Override
	public void run(final InfoProvider provider) {
		// No changes to state to apply
	}

	@Override
	public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
		return 100;
	}

	@Override
	public boolean shouldAddTraceabilityFieldsToRecord() {
		return false;
	}

	@Override
	public long getGasRequirement(long blockTimestamp) {
		final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
		return pricingUtils.computeViewFunctionGas(now, getMinimumFeeInTinybars(now));
	}
}
