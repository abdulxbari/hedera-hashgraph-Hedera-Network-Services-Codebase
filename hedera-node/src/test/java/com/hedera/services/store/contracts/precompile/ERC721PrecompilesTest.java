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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_APPROVE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_DECIMALS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_GET_APPROVED;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_OWNER_OF_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SYMBOL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_TRANSFER_FROM;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.failResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.missingNftResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.ownerOfAndTokenUriWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.precompiledContract;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.recipientAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.serialNumber;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.token;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static org.hyperledger.besu.datatypes.Address.RIPEMD160;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hedera.services.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ERC721PrecompilesTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private PrecompileMessage precompileMessage;
    @Mock private PrecompileInfoProvider precompileInfoProvider;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private DecodingFacade decoder;
    @Mock private EncodingFacade encoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private FeeObject mockFeeObject;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private CryptoApproveAllowanceTransactionBody cryptoApproveAllowanceTransactionBody;
    @Mock private DeleteAllowanceChecks deleteAllowanceChecks;
    @Mock private ApproveAllowanceChecks allowanceChecks;
    @Mock private AccountStore accountStore;
    @Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
    @Mock private CryptoDeleteAllowanceTransactionBody cryptoDeleteAllowanceTransactionBody;
    @Mock private ContractAliases aliases;
    @Mock private ImpliedTransfers impliedTransfers;
    @Mock private ImpliedTransfersMeta impliedTransfersMeta;
    @Mock private ApproveAllowanceLogic approveAllowanceLogic;
    @Mock private DeleteAllowanceLogic deleteAllowanceLogic;
    @Mock private TransferLogic transferLogic;
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;

    @BeforeEach
    void setUp() {
        PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        decoder,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(nonFungibleTokenAddr.toArray()))
                .thenReturn(token);
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.contractIdFromEvmAddress(
                                        Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS)
                                                .toArray()))
                .thenReturn(precompiledContract);
        entityIdUtils
                .when(() -> EntityIdUtils.accountIdFromEvmAddress(senderAddress))
                .thenReturn(sender);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(token)).thenReturn(tokenAddress);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(sender)).thenReturn(senderAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(receiver))
                .thenReturn(recipientAddress);
        entityIdUtils
                .when(() -> EntityIdUtils.asEvmAddress(0, 0, 3))
                .thenReturn(RIPEMD160.toArray());
        entityIdUtils
                .when(() -> EntityIdUtils.asEvmAddress(0, 0, 2))
                .thenReturn(RIPEMD160.toArray());
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
    }

    @Test
    void name() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_NAME));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(encoder.encodeName(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void symbol() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_SYMBOL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(encoder.encodeSymbol(any())).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void isApprovedForAllWorksWithBothOwnerAndOperatorExtant() {
        Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        FcTokenAllowanceId fcTokenAllowanceId =
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);
        givenInfoProvider();

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.operator())).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(encoder.encodeIsApprovedForAll(true)).willReturn(successResult);
        given(decoder.decodeIsApprovedForAll(eq(nestedPretendArguments), any()))
                .willReturn(IS_APPROVE_FOR_ALL_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(accounts.get(any(), any())).willReturn(allowances);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void isApprovedForAllWorksWithOperatorMissing() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_IS_APPROVED_FOR_ALL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.contains(IS_APPROVE_FOR_ALL_WRAPPER.owner())).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(encoder.encodeIsApprovedForAll(false)).willReturn(successResult);
        given(decoder.decodeIsApprovedForAll(eq(nestedPretendArguments), any()))
                .willReturn(IS_APPROVE_FOR_ALL_WRAPPER);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approve() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();
        givenInfoProvider();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER);
        given(encoder.encodeApprove(true)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveRevertsIfItFails() {
        givenPricingUtilsContext();
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER);
        given(infrastructureFactory.newApproveAllowanceLogic(any(), any()))
                .willReturn(approveAllowanceLogic);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
        willThrow(new InvalidTransactionException(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO))
                .given(approveAllowanceLogic)
                .approveAllowance(any(), any(), any(), any());

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);
        final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

        verify(frame)
                .setRevertReason(Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void approveSpender0WhenOwner() {
        givenPricingUtilsContext();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(
                        syntheticTxnFactory.createDeleteAllowance(
                                APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance())
                .willReturn(cryptoDeleteAllowanceTransactionBody);
        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList())
                .willReturn(List.of(NftRemoveAllowance.newBuilder().setOwner(sender).build()));

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any())).willReturn(OK);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER_0);
        given(encoder.encodeApprove(true)).willReturn(successResult);
        given(infrastructureFactory.newDeleteAllowanceLogic(any(), any()))
                .willReturn(deleteAllowanceLogic);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveSpender0WhenGrantedApproveForAll() {
        givenPricingUtilsContext();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(
                        syntheticTxnFactory.createDeleteAllowance(
                                APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance())
                .willReturn(cryptoDeleteAllowanceTransactionBody);
        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList())
                .willReturn(
                        List.of(NftRemoveAllowance.newBuilder().setOwner(feeCollector).build()));
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any())).willReturn(OK);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER_0);
        given(encoder.encodeApprove(true)).willReturn(successResult);
        given(infrastructureFactory.newDeleteAllowanceLogic(any(), any()))
                .willReturn(deleteAllowanceLogic);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void approveSpender0NoGoodIfNotPermissioned() {
        givenPricingUtilsContext();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(
                        syntheticTxnFactory.createDeleteAllowance(
                                APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER_0);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);
        final var expectedFailure = EncodingFacade.resultFrom(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);

        verify(frame)
                .setRevertReason(Bytes.of(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void validatesImpliedNftApprovalDeletion() {
        givenPricingUtilsContext();
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(
                        syntheticTxnFactory.createDeleteAllowance(
                                APPROVE_WRAPPER_0, EntityId.fromGrpcAccountId(sender)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoDeleteAllowance())
                .willReturn(cryptoDeleteAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(cryptoDeleteAllowanceTransactionBody.getNftAllowancesList())
                .willReturn(Collections.emptyList());
        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER_0);
        given(deleteAllowanceChecks.deleteAllowancesValidation(any(), any(), any()))
                .willReturn(INVALID_ALLOWANCE_OWNER_ID);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);
        final var expectedFailure = EncodingFacade.resultFrom(INVALID_ALLOWANCE_OWNER_ID);

        verify(frame).setRevertReason(Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes()));
        assertEquals(expectedFailure, result);
    }

    @Test
    void allowanceValidation() {
        givenPricingUtilsContext();
        givenInfoProvider();
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_APPROVE));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(wrappedLedgers.ownerIfPresent(any())).willReturn(senderId);
        given(wrappedLedgers.hasApprovedForAll(any(), any(), any())).willReturn(true);
        given(syntheticTxnFactory.createNonfungibleApproval(eq(APPROVE_WRAPPER), any(), any()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);
        given(infrastructureFactory.newDeleteAllowanceChecks()).willReturn(deleteAllowanceChecks);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(FAIL_INVALID);

        given(decoder.decodeTokenApprove(eq(nestedPretendArguments), eq(token), eq(false), any()))
                .willReturn(APPROVE_WRAPPER);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(failResult, result);
    }

    @Test
    void setApprovalForAll() {
        List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
        List<TokenAllowance> tokenAllowances = new ArrayList<>();
        List<NftAllowance> nftAllowances = new ArrayList<>();

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_SET_APPROVAL_FOR_ALL));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();
        givenInfoProvider();

        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(
                        syntheticTxnFactory.createApproveAllowanceForAllNFT(
                                SET_APPROVAL_FOR_ALL_WRAPPER, token))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoApproveAllowance())
                .willReturn(cryptoApproveAllowanceTransactionBody);

        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newApproveAllowanceLogic(accountStore, tokenStore))
                .willReturn(approveAllowanceLogic);
        given(EntityIdUtils.accountIdFromEvmAddress((Address) any())).willReturn(sender);
        given(accountStore.loadAccount(any())).willReturn(new Account(accountId));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(infrastructureFactory.newApproveAllowanceChecks()).willReturn(allowanceChecks);

        given(
                        allowanceChecks.allowancesValidation(
                                cryptoAllowances,
                                tokenAllowances,
                                nftAllowances,
                                new Account(accountId),
                                stateView))
                .willReturn(OK);

        given(decoder.decodeSetApprovalForAll(eq(nestedPretendArguments), any()))
                .willReturn(SET_APPROVAL_FOR_ALL_WRAPPER);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getApproved() {
        Set<FcTokenAllowanceId> allowances = new TreeSet<>();
        FcTokenAllowanceId fcTokenAllowanceId =
                FcTokenAllowanceId.from(
                        EntityNum.fromLong(token.getTokenNum()),
                        EntityNum.fromLong(receiver.getAccountNum()));
        allowances.add(fcTokenAllowanceId);

        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_GET_APPROVED));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(wrappedLedgers.nfts()).willReturn(nfts);
        final var nftId = NftId.fromGrpc(token, GET_APPROVED_WRAPPER.serialNo());
        given(nfts.contains(nftId)).willReturn(true);
        given(nfts.get(nftId, NftProperty.SPENDER)).willReturn(EntityId.fromAddress(RIPEMD160));
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(encoder.encodeGetApproved(RIPEMD160)).willReturn(successResult);
        given(decoder.decodeGetApproved(nestedPretendArguments)).willReturn(GET_APPROVED_WRAPPER);
        given(wrappedLedgers.canonicalAddress(any())).willReturn(RIPEMD160);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void totalSupply() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOTAL_SUPPLY_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();
        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(wrappedLedgers.totalSupplyOf(any())).willReturn(10L);
        given(encoder.encodeTotalSupply(10L)).willReturn(successResult);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        assertEquals(successResult, result);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void balanceOf() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_BALANCE_OF_TOKEN));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(syntheticTxnFactory.createTransactionCall(1L, nestedPretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(decoder.decodeBalanceOf(eq(nestedPretendArguments), any()))
                .willReturn(BALANCE_OF_WRAPPER);
        given(wrappedLedgers.balanceOf(any(), any())).willReturn(10L);
        given(encoder.encodeBalance(10L)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then:
        assertEquals(successResult, subject.computeInternal(precompileInfoProvider));
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void ownerOfHappyPathWorks() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(decoder.decodeOwnerOf(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(NftId.fromGrpc(token, ownerOfAndTokenUriWrapper.serialNo())))
                .willReturn(true);
        given(wrappedLedgers.ownerOf(any())).willReturn(senderAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(encoder.encodeOwner(senderAddress)).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void ownerOfRevertsWithMissingNft() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(decoder.decodeOwnerOf(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.nfts()).willReturn(nfts);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        assertEquals(missingNftResult, result);
    }

    @Test
    void transferFrom() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();
        givenInfoProvider();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(
                        decoder.decodeERCTransferFrom(
                                eq(nestedPretendArguments), any(), eq(false), any(), any(), any()))
                .willReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));
        final var nftId = NftId.fromGrpc(token, serialNumber);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.canonicalAddress(recipientAddress)).willReturn(recipientAddress);
        given(wrappedLedgers.canonicalAddress(senderAddress)).willReturn(senderAddress);
        given(nfts.contains(nftId)).willReturn(true);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);

        final var log =
                EncodingFacade.LogBuilder.logBuilder()
                        .forLogger(tokenAddress)
                        .forEventSignature(AbiConstants.TRANSFER_EVENT)
                        .forIndexedArgument(senderAddress)
                        .forIndexedArgument(recipientAddress)
                        .forIndexedArgument(serialNumber)
                        .build();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(Bytes.EMPTY, result);

        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(frame).addLog(log);
    }

    @Test
    void transferFromFailsForInvalidSig() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER_FROM));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenLedgers();
        givenPricingUtilsContext();
        givenInfoProvider();

        given(frame.getContractAddress()).willReturn(contractAddr);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(TOKEN_TRANSFER_WRAPPER)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(false);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);

        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(
                        decoder.decodeERCTransferFrom(
                                eq(nestedPretendArguments), any(), eq(false), any(), any(), any()))
                .willReturn(Collections.singletonList(TOKEN_TRANSFER_WRAPPER));
        final var nftId = NftId.fromGrpc(token, serialNumber);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(nfts.contains(nftId)).willReturn(true);

        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(invalidSigResult, result);
    }

    @Test
    void erc721SystemFailureSurfacesResult() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_OWNER_OF_NFT));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(decoder.decodeOwnerOf(nestedPretendArguments)).willReturn(ownerOfAndTokenUriWrapper);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        assertEquals(failResult, result);
    }

    @Test
    void tokenURI() {
        Bytes nestedPretendArguments = Bytes.of(Integers.toBytes(ABI_ID_ERC_TOKEN_URI_NFT));
        Bytes pretendArguments = givenMinimalFrameContext(nestedPretendArguments);
        givenInfoProvider();

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(decoder.decodeTokenUriNFT(nestedPretendArguments))
                .willReturn(ownerOfAndTokenUriWrapper);
        given(wrappedLedgers.metadataOf(any())).willReturn("Metadata");
        given(encoder.encodeTokenUri("Metadata")).willReturn(successResult);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(precompileInfoProvider);

        // then:
        assertEquals(successResult, result);
    }

    @Test
    void transferNotSupported() {
        Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_TRANSFER)));
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    @Test
    void decimalsNotSupported() {
        Bytes pretendArguments =
                givenMinimalFrameContextWithoutParentUpdater(
                        Bytes.of(Integers.toBytes(ABI_ID_ERC_DECIMALS)));
        given(wrappedLedgers.typeOf(token)).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.prepareFields(frame);

        final var exception =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.prepareComputation(pretendArguments, a -> a));
        assertEquals(NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, exception.getMessage());
    }

    private Bytes givenMinimalFrameContext(Bytes nestedPretendArguments) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                nonFungibleTokenAddr,
                nestedPretendArguments);
    }

    private Bytes givenMinimalFrameContextWithoutParentUpdater(Bytes nestedPretendArguments) {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                nonFungibleTokenAddr,
                nestedPretendArguments);
    }

    private void givenInfoProvider() {
        precompileInfoProvider =
                dynamicProperties.enableDirectHTSTokenCalls()
                        ? new DirectCallsPrecompileInfoProvider(precompileMessage)
                        : new EVMPrecompileInfoProvider(frame);
    }

    public static final BalanceOfWrapper BALANCE_OF_WRAPPER = new BalanceOfWrapper(sender);
    public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER =
            new TokenTransferWrapper(
                    List.of(new SyntheticTxnFactory.NftExchange(1, token, sender, receiver)),
                    new ArrayList<>() {});

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    public static final IsApproveForAllWrapper IS_APPROVE_FOR_ALL_WRAPPER =
            new IsApproveForAllWrapper(sender, receiver);

    public static final GetApprovedWrapper GET_APPROVED_WRAPPER =
            new GetApprovedWrapper(token.getTokenNum());

    public static final SetApprovalForAllWrapper SET_APPROVAL_FOR_ALL_WRAPPER =
            new SetApprovalForAllWrapper(receiver, true);

    public static final ApproveWrapper APPROVE_WRAPPER =
            new ApproveWrapper(token, receiver, BigInteger.ZERO, BigInteger.ONE, false);

    public static final ApproveWrapper APPROVE_WRAPPER_0 =
            new ApproveWrapper(
                    token, IdUtils.asAccount("0.0.0"), BigInteger.ZERO, BigInteger.ONE, false);
}
