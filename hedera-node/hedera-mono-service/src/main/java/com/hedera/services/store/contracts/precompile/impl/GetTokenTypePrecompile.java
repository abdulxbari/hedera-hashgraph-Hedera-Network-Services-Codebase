/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmGetTokenTypePrecompile;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenTypePrecompile extends AbstractTokenInfoPrecompile
        implements EvmGetTokenTypePrecompile {

    public GetTokenTypePrecompile(
            TokenID tokenId,
            SyntheticTxnFactory syntheticTxnFactory,
            WorldLedgers ledgers,
            EncodingFacade encoder,
            PrecompilePricingUtils pricingUtils,
            StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils, stateView);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decodeGetTokenType(input);
        tokenId = tokenInfoWrapper.tokenID();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var token = ledgers.tokens().getImmutableRef(tokenId);
        validateTrue(token != null, ResponseCodeEnum.INVALID_TOKEN_ID);
        final var tokenType = token.tokenType().ordinal();
        return encoder.encodeGetTokenType(tokenType);
    }

    public static TokenInfoWrapper decodeGetTokenType(final Bytes input) {
        return EvmGetTokenTypePrecompile.decodeGetTokenType(input);
    }
}
