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

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class TokenInfoPrecompile extends AbstractTokenInfoPrecompile {
    private static final Function GET_TOKEN_INFO_FUNCTION = new Function("getTokenInfo(address)");
    private static final Bytes GET_TOKEN_INFO_SELECTOR =
            Bytes.wrap(GET_TOKEN_INFO_FUNCTION.selector());
    private static final ABIType<Tuple> GET_TOKEN_INFO_DECODER = TypeFactory.create(BYTES32);

    public TokenInfoPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils,
            final StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils, stateView);
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var tokenInfoWrapper = decodeGetTokenInfo(input);
        tokenId = tokenInfoWrapper.tokenID();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var tokenInfo =
                ledgers.infoForToken(tokenId, stateView.getNetworkInfo().ledgerId()).orElse(null);
        validateTrue(tokenInfo != null, ResponseCodeEnum.INVALID_TOKEN_ID);

        return encoder.encodeGetTokenInfo(tokenInfo);
    }

    public static TokenInfoWrapper decodeGetTokenInfo(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, GET_TOKEN_INFO_SELECTOR, GET_TOKEN_INFO_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        return TokenInfoWrapper.forToken(tokenID);
    }
}