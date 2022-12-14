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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_DECODER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade.removeBrackets;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.AbstractTokenUpdatePrecompile.UpdateType.UPDATE_TOKEN_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils.validateAdminKey;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils.validateTokenKeysInput;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenKeyWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenUpdateWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenUpdatePrecompile extends AbstractTokenUpdatePrecompile {

    private static final String UPDATE_TOKEN_INFO_STRING = "updateTokenInfo(address,";
    private static final Function TOKEN_UPDATE_INFO_FUNCTION =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR =
            Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION.selector());
    private static final ABIType<Tuple> TOKEN_UPDATE_INFO_DECODER =
            TypeFactory.create(
                    "(" + removeBrackets(BYTES32) + "," + HEDERA_TOKEN_STRUCT_DECODER + ")");
    private static final Function TOKEN_UPDATE_INFO_FUNCTION_V2 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V2 + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR_V2 =
            Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION_V2.selector());
    private static final Function TOKEN_UPDATE_INFO_FUNCTION_V3 =
            new Function(UPDATE_TOKEN_INFO_STRING + HEDERA_TOKEN_STRUCT_V3 + ")");
    private static final Bytes TOKEN_UPDATE_INFO_SELECTOR_V3 =
            Bytes.wrap(TOKEN_UPDATE_INFO_FUNCTION_V3.selector());
    private static final String TREASURY_ACCOUNT_SIGNATURE_MISSING_IN_TOKEN_UPDATE =
            "Treasury account signature missing in token update!";
    private static final String NEW_ADMIN_ACCOUNT_SIGNATURE_MISSING_IN_TOKEN_UPDATE =
            "New admin account signature missing in token update!";
    private TokenUpdateWrapper updateOp;
    private final int functionId;
    private final Address senderAddress;

    public TokenUpdatePrecompile(
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffectsTracker,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils precompilePricingUtils,
            final int functionId,
            final Address senderAddress) {
        super(
                ledgers,
                aliases,
                sigsVerifier,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);

        this.functionId = functionId;
        this.senderAddress = senderAddress;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        updateOp =
                switch (functionId) {
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO -> decodeUpdateTokenInfo(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V2 -> decodeUpdateTokenInfoV2(
                            input, aliasResolver);
                    case AbiConstants.ABI_ID_UPDATE_TOKEN_INFO_V3 -> decodeUpdateTokenInfoV3(
                            input, aliasResolver);
                    default -> null;
                };
        Objects.requireNonNull(updateOp);
        final var tokenKeys = updateOp.tokenKeys();
        validateTokenKeysInput(tokenKeys);
        transactionBody = syntheticTxnFactory.createTokenUpdate(updateOp);
        return transactionBody;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(updateOp);
        validateTrue(updateOp.tokenID() != null, INVALID_TOKEN_ID);
        tokenId = Id.fromGrpcToken(updateOp.tokenID());
        type = UPDATE_TOKEN_INFO;

        if (updateOp.treasury() != null) {
            final var treasuryId = Id.fromGrpcAccount(updateOp.treasury());
            final var treasuryHasSigned =
                    KeyActivationUtils.validateKey(
                            frame,
                            treasuryId.asEvmAddress(),
                            sigsVerifier::hasActiveKey,
                            ledgers,
                            aliases);

            validateTrue(
                    treasuryHasSigned,
                    INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE,
                    TREASURY_ACCOUNT_SIGNATURE_MISSING_IN_TOKEN_UPDATE);
        }

        updateOp.tokenKeys().stream()
                .filter(TokenKeyWrapper::isUsedForAdminKey)
                .findFirst()
                .ifPresent(
                        key ->
                                validateTrue(
                                        validateAdminKey(
                                                frame,
                                                key,
                                                senderAddress,
                                                sigsVerifier,
                                                ledgers,
                                                aliases),
                                        INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE,
                                        NEW_ADMIN_ACCOUNT_SIGNATURE_MISSING_IN_TOKEN_UPDATE));

        super.run(frame);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeUpdateTokenInfoV2(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address))[],(uint32,address,uint32)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfo(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR);
    }

    /**
     * Decodes the given bytes of the updateTokenInfo function.
     *
     * <p><b>Important: </b>This is an old version and is superseded by
     * decodeNonFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(uint32,address,uint32)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfoV2(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the updateTokenInfo function.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeNonFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * updateTokenInfo(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenUpdateWrapper codec
     */
    public static TokenUpdateWrapper decodeUpdateTokenInfoV3(
            Bytes input, UnaryOperator<byte[]> aliasResolver) {
        return getTokenUpdateWrapper(input, aliasResolver, TOKEN_UPDATE_INFO_SELECTOR_V3);
    }

    private static TokenUpdateWrapper getTokenUpdateWrapper(
            Bytes input, UnaryOperator<byte[]> aliasResolver, Bytes tokenUpdateInfoSelector) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, tokenUpdateInfoSelector, TOKEN_UPDATE_INFO_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        final Tuple hederaTokenStruct = decodedArguments.get(1);
        final var tokenName = (String) hederaTokenStruct.get(0);
        final var tokenSymbol = (String) hederaTokenStruct.get(1);
        final var tokenTreasury =
                convertLeftPaddedAddressToAccountId(hederaTokenStruct.get(2), aliasResolver);
        final var tokenMemo = (String) hederaTokenStruct.get(3);
        final var tokenKeys = decodeTokenKeys(hederaTokenStruct.get(7), aliasResolver);
        final var tokenExpiry = decodeTokenExpiry(hederaTokenStruct.get(8), aliasResolver);
        return new TokenUpdateWrapper(
                tokenID,
                tokenName,
                tokenSymbol,
                tokenTreasury.getAccountNum() == 0 ? null : tokenTreasury,
                tokenMemo,
                tokenKeys,
                tokenExpiry);
    }
}
