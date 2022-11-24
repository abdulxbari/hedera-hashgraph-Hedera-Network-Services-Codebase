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

import static com.hedera.services.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class IsTokenPrecompile extends AbstractTokenInfoPrecompile {
    private static final Function IS_TOKEN_FUNCTION =
            new Function("isToken(address)", INT_BOOL_PAIR);
    private static final Bytes IS_TOKEN_FUNCTION_SELECTOR =
            Bytes.wrap(IS_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> IS_TOKEN_DECODER = TypeFactory.create(BYTES32);

    public IsTokenPrecompile(
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
        final var tokenInfoWrapper = decodeIsToken(input);
        tokenId = tokenInfoWrapper.tokenID();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        final var isToken = stateView.tokenExists(tokenId);
        return encoder.encodeIsToken(isToken);
    }

    public static TokenInfoWrapper decodeIsToken(final Bytes input) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, IS_TOKEN_FUNCTION_SELECTOR, IS_TOKEN_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        return TokenInfoWrapper.forToken(tokenID);
    }
}
