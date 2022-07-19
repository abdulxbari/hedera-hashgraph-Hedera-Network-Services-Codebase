/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.utils.DescriptorUtils.isTokenProxyRedirect;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DecimalsPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.NamePrecompile;
import com.hedera.services.store.contracts.precompile.impl.OwnerOfPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SymbolPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenURIPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TotalSupplyPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.utils.DescriptorUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompileUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.jetbrains.annotations.NotNull;

@Singleton
public class HTSPrecompiledContract extends AbstractPrecompiledContract {
    private static final Logger log = LogManager.getLogger(HTSPrecompiledContract.class);

    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID =
            contractIdFromEvmAddress(
                    Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());
    public static final EntityId HTS_PRECOMPILE_MIRROR_ENTITY_ID =
            EntityId.fromGrpcContractId(HTS_PRECOMPILE_MIRROR_ID);

    private static final PrecompileContractResult NO_RESULT =
            new PrecompileContractResult(
                    null, true, MessageFrame.State.COMPLETED_FAILED, Optional.empty());

    private static final Bytes STATIC_CALL_REVERT_REASON =
            Bytes.of("HTS precompiles are not static".getBytes());
    private static final String NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON =
            "Invalid operation for ERC-20 token!";
    private static final String NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON =
            "Invalid operation for ERC-721 token!";
    private static final Bytes ERROR_DECODING_INPUT_REVERT_REASON =
            Bytes.of("Error decoding precompile input".getBytes());
    public static final String URI_QUERY_NON_EXISTING_TOKEN_ERROR =
            "ERC721Metadata: URI query for nonexistent token";

    private final EntityCreator creator;
    private final DecodingFacade decoder;
    private final EncodingFacade encoder;
    private final GlobalDynamicProperties dynamicProperties;
    private final EvmSigsVerifier sigsVerifier;
    private final RecordsHistorian recordsHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final InfrastructureFactory infrastructureFactory;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;

    private Precompile precompile;
    private TransactionBody.Builder transactionBody;
    private final Provider<FeeCalculator> feeCalculator;
    private long gasRequirement = 0;
    private final StateView currentView;
    private SideEffectsTracker sideEffectsTracker;
    private final PrecompilePricingUtils precompilePricingUtils;
    private WorldLedgers ledgers;
    private Address senderAddress;
    private HederaStackedWorldStateUpdater updater;
    private PrecompileInfoProvider precompileInfoProvider;
    private boolean hasRedirectBytes;

    @Inject
    public HTSPrecompiledContract(
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final RecordsHistorian recordsHistorian,
            final TxnAwareEvmSigsVerifier sigsVerifier,
            final DecodingFacade decoder,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final ExpiringCreations creator,
            final ImpliedTransfersMarshal impliedTransfersMarshal,
            final Provider<FeeCalculator> feeCalculator,
            final StateView currentView,
            final PrecompilePricingUtils precompilePricingUtils,
            final InfrastructureFactory infrastructureFactory) {
        super("HTS", gasCalculator);
        this.decoder = decoder;
        this.encoder = encoder;
        this.sigsVerifier = sigsVerifier;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.creator = creator;
        this.dynamicProperties = dynamicProperties;
        this.impliedTransfersMarshal = impliedTransfersMarshal;
        this.feeCalculator = feeCalculator;
        this.currentView = currentView;
        this.precompilePricingUtils = precompilePricingUtils;
        this.infrastructureFactory = infrastructureFactory;
    }

    public Pair<Long, Bytes> computeCosted(final Bytes input, final MessageFrame frame) {
        if (frame.isStatic()) {
            if (!isTokenProxyRedirect(input)) {
                frame.setRevertReason(STATIC_CALL_REVERT_REASON);
                return Pair.of(defaultGas(), null);
            } else {
                final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
                if (!proxyUpdater.isInTransaction()) {
                    final var executor =
                            infrastructureFactory.newRedirectExecutor(
                                    input, frame, precompilePricingUtils::computeViewFunctionGas);
                    return executor.computeCosted();
                }
            }
        }
        final var result = computePrecompile(input, frame);
        return Pair.of(gasRequirement, result.getOutput());
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @NotNull
    @Override
    public PrecompileContractResult computePrecompile(
            final Bytes input, @NotNull final MessageFrame frame) {
        prepareFields(frame);
        prepareComputation(input, updater::unaliased);

        gasRequirement = defaultGas();
        if (this.precompile == null || this.transactionBody == null) {
            frame.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
            return NO_RESULT;
        }

        final var now = frame.getBlockValues().getTimestamp();
        gasRequirement = precompile.getGasRequirement(now);
        Bytes result = computeInternal(precompileInfoProvider);

        return result == null
                ? PrecompiledContract.PrecompileContractResult.halt(
                        null, Optional.of(ExceptionalHaltReason.NONE))
                : PrecompiledContract.PrecompileContractResult.success(result);
    }

    public void callHtsPrecompileDirectly(PrecompileMessage message) {
        final var inputData = message.getInputData();
        final var now = message.getConsensusTime();
        sideEffectsTracker = infrastructureFactory.newSideEffects();
        ledgers = message.getLedgers().wrapped(sideEffectsTracker);
        senderAddress = message.getSenderAddress();
        precompileInfoProvider = new DirectCallsPrecompileInfoProvider(message);
        resetPrecompileFields();

        precompile = constructRedirectPrecompile(inputData.getInt(0), message.getTokenID());
        gasRequirement = defaultGas();

        if (precompile != null) {
            decodeInput(inputData, message::unaliased);
            gasRequirement = precompile.getGasRequirement(now);
            message.setHtsOutputResult(computeInternal(precompileInfoProvider));
        } else if (transactionBody == null) {
            message.setRevertReason(ERROR_DECODING_INPUT_REVERT_REASON);
        }
        message.setGasRequired(gasRequirement);
    }

    void prepareFields(final MessageFrame frame) {
        this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        this.sideEffectsTracker = infrastructureFactory.newSideEffects();
        this.ledgers = updater.wrappedTrackingLedgers(sideEffectsTracker);
        this.precompileInfoProvider = new EVMPrecompileInfoProvider(frame);

        final var unaliasedSenderAddress =
                updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
        this.senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));
    }

    void prepareComputation(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        resetPrecompileFields();
        int functionId = input.getInt(0);

        this.precompile =
                switch (functionId) {
                    case AbiConstants.ABI_ID_CRYPTO_TRANSFER,
                            AbiConstants.ABI_ID_TRANSFER_TOKENS,
                            AbiConstants.ABI_ID_TRANSFER_TOKEN,
                            AbiConstants.ABI_ID_TRANSFER_NFTS,
                            AbiConstants.ABI_ID_TRANSFER_NFT -> new TransferPrecompile(
                            ledgers,
                            decoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            functionId,
                            senderAddress,
                            impliedTransfersMarshal);
                    case AbiConstants.ABI_ID_MINT_TOKEN -> new MintPrecompile(
                            ledgers,
                            decoder,
                            encoder,
                            sigsVerifier,
                            recordsHistorian,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_BURN_TOKEN -> new BurnPrecompile(
                            ledgers,
                            decoder,
                            encoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils);
                    case AbiConstants.ABI_ID_ASSOCIATE_TOKENS -> new MultiAssociatePrecompile(
                            ledgers,
                            decoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_ASSOCIATE_TOKEN -> new AssociatePrecompile(
                            ledgers,
                            decoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_DISSOCIATE_TOKENS -> new MultiDissociatePrecompile(
                            ledgers,
                            decoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_DISSOCIATE_TOKEN -> new DissociatePrecompile(
                            ledgers,
                            decoder,
                            sigsVerifier,
                            sideEffectsTracker,
                            syntheticTxnFactory,
                            infrastructureFactory,
                            precompilePricingUtils,
                            feeCalculator,
                            currentView);
                    case AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN -> tokenRedirectCase(input);
                    case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN,
                            AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES,
                            AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN,
                            AbiConstants
                                    .ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> (dynamicProperties
                                    .isHTSPrecompileCreateEnabled())
                            ? new TokenCreatePrecompile(
                                    ledgers,
                                    decoder,
                                    encoder,
                                    updater,
                                    sigsVerifier,
                                    recordsHistorian,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    functionId,
                                    senderAddress,
                                    dynamicProperties.fundingAccount(),
                                    feeCalculator,
                                    precompilePricingUtils)
                            : null;
                    default -> null;
                };
        if (precompile != null) {
            decodeInput(input, aliasResolver);
        }
    }

    private Precompile tokenRedirectCase(Bytes input) {
        final var target = DescriptorUtils.getRedirectTarget(input);
        final var tokenId = target.tokenId();
        final var functionSelector = target.descriptor();
        hasRedirectBytes = true;
        return constructRedirectPrecompile(functionSelector, tokenId);
    }

    private Precompile constructRedirectPrecompile(int functionSelector, TokenID tokenId) {
        final var isFungibleToken = TokenType.FUNGIBLE_COMMON.equals(ledgers.typeOf(tokenId));
        final var functionId = AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;

        return switch (functionSelector) {
            case AbiConstants.ABI_ID_ERC_NAME -> new NamePrecompile(
                    tokenId,
                    syntheticTxnFactory,
                    ledgers,
                    encoder,
                    decoder,
                    precompilePricingUtils);
            case AbiConstants.ABI_ID_ERC_SYMBOL -> new SymbolPrecompile(
                    tokenId,
                    syntheticTxnFactory,
                    ledgers,
                    encoder,
                    decoder,
                    precompilePricingUtils);
            case AbiConstants.ABI_ID_ERC_DECIMALS -> checkFungible(
                    isFungibleToken,
                    () ->
                            new DecimalsPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            case AbiConstants.ABI_ID_ERC_TOTAL_SUPPLY_TOKEN -> new TotalSupplyPrecompile(
                    tokenId,
                    syntheticTxnFactory,
                    ledgers,
                    encoder,
                    decoder,
                    precompilePricingUtils);
            case AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN -> new BalanceOfPrecompile(
                    tokenId,
                    syntheticTxnFactory,
                    ledgers,
                    encoder,
                    decoder,
                    precompilePricingUtils);
            case AbiConstants.ABI_ID_ERC_OWNER_OF_NFT -> checkNFT(
                    isFungibleToken,
                    () ->
                            new OwnerOfPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            case AbiConstants.ABI_ID_ERC_TOKEN_URI_NFT -> checkNFT(
                    isFungibleToken,
                    () ->
                            new TokenURIPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            case AbiConstants.ABI_ID_ERC_TRANSFER -> checkFungible(
                    isFungibleToken,
                    () ->
                            new ERCTransferPrecompile(
                                    tokenId,
                                    senderAddress,
                                    isFungibleToken,
                                    ledgers,
                                    decoder,
                                    encoder,
                                    sigsVerifier,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    precompilePricingUtils,
                                    functionId,
                                    impliedTransfersMarshal));
            case AbiConstants.ABI_ID_ERC_TRANSFER_FROM -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new ERCTransferPrecompile(
                                    tokenId,
                                    senderAddress,
                                    isFungibleToken,
                                    ledgers,
                                    decoder,
                                    encoder,
                                    sigsVerifier,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    precompilePricingUtils,
                                    functionId,
                                    impliedTransfersMarshal));
            case AbiConstants.ABI_ID_ERC_ALLOWANCE -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new AllowancePrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            case AbiConstants.ABI_ID_ERC_APPROVE -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new ApprovePrecompile(
                                    tokenId,
                                    isFungibleToken,
                                    ledgers,
                                    decoder,
                                    encoder,
                                    currentView,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    precompilePricingUtils,
                                    senderAddress));
            case AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new SetApprovalForAllPrecompile(
                                    tokenId,
                                    ledgers,
                                    decoder,
                                    currentView,
                                    sideEffectsTracker,
                                    syntheticTxnFactory,
                                    infrastructureFactory,
                                    precompilePricingUtils,
                                    senderAddress));
            case AbiConstants.ABI_ID_ERC_GET_APPROVED -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new GetApprovedPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            case AbiConstants.ABI_ID_ERC_IS_APPROVED_FOR_ALL -> checkFeatureFlag(
                    dynamicProperties.areAllowancesEnabled(),
                    () ->
                            new IsApprovedForAllPrecompile(
                                    tokenId,
                                    syntheticTxnFactory,
                                    ledgers,
                                    encoder,
                                    decoder,
                                    precompilePricingUtils));
            default -> null;
        };
    }

    /* --- Helpers --- */
    private void resetPrecompileFields() {
        precompile = null;
        transactionBody = null;
        gasRequirement = 0L;
        hasRedirectBytes = false;
    }

    void decodeInput(Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        input = hasRedirectBytes ? input.slice(24) : input;
        this.transactionBody = TransactionBody.newBuilder();
        try {
            this.transactionBody = this.precompile.body(input, aliasResolver);
        } catch (Exception e) {
            log.warn("Internal precompile failure", e);
            transactionBody = null;
        }
    }

    private Precompile checkNFT(boolean isFungible, Supplier<Precompile> precompileSupplier) {
        if (isFungible) {
            throw new InvalidTransactionException(
                    NOT_SUPPORTED_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFungible(boolean isFungible, Supplier<Precompile> precompileSupplier) {
        if (!isFungible) {
            throw new InvalidTransactionException(
                    NOT_SUPPORTED_NON_FUNGIBLE_OPERATION_REASON, INVALID_TOKEN_ID);
        } else {
            return precompileSupplier.get();
        }
    }

    private Precompile checkFeatureFlag(
            boolean featureFlag, Supplier<Precompile> precompileSupplier) {
        if (!featureFlag) {
            throw new InvalidTransactionException(NOT_SUPPORTED);
        } else {
            return precompileSupplier.get();
        }
    }

    @SuppressWarnings("rawtypes")
    protected Bytes computeInternal(final PrecompileInfoProvider provider) {
        Bytes result;
        ExpirableTxnRecord.Builder childRecord;
        try {
            validateTrue(provider.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

            precompile.handleSentHbars(provider);
            precompile.customizeTrackingLedgers(ledgers);
            precompile.run(provider);

            // As in HederaLedger.commit(), we must first commit the ledgers before creating our
            // synthetic record, as the ledger interceptors will populate the sideEffectsTracker
            ledgers.commit();

            childRecord =
                    creator.createSuccessfulSyntheticRecord(
                            precompile.getCustomFees(), sideEffectsTracker, EMPTY_MEMO);
            result = precompile.getSuccessResultFor(childRecord);
            addContractCallResultToRecord(childRecord, result, Optional.empty(), provider);
        } catch (final InvalidTransactionException e) {
            final var status = e.getResponseCode();
            childRecord = creator.createUnsuccessfulSyntheticRecord(status);
            result = precompile.getFailureResultFor(status);
            addContractCallResultToRecord(childRecord, result, Optional.of(status), provider);
            if (e.isReverting()) {
                provider.setState(MessageFrame.State.REVERT);
                provider.setRevertReason(e.getRevertReason());
            }
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            childRecord = creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID);
            result = precompile.getFailureResultFor(FAIL_INVALID);
            addContractCallResultToRecord(
                    childRecord, result, Optional.of(FAIL_INVALID), precompileInfoProvider);
        }

        if (provider.isDirectTokenCall()) {
            recordsHistorian.trackFollowingChildRecord(
                    recordsHistorian.nextChildRecordSourceId(), transactionBody, childRecord);
        } else {
            // This should always have a parent stacked updater
            final var parentUpdater = updater.parentUpdater();
            if (parentUpdater.isPresent()) {
                final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
                parent.manageInProgressRecord(recordsHistorian, childRecord, this.transactionBody);
            } else {
                throw new InvalidTransactionException(
                        "HTS precompile frame had no parent updater", FAIL_INVALID);
            }
        }

        return result;
    }

    private void addContractCallResultToRecord(
            final ExpirableTxnRecord.Builder childRecord,
            final Bytes result,
            final Optional<ResponseCodeEnum> errorStatus,
            final PrecompileInfoProvider provider) {
        if (dynamicProperties.shouldExportPrecompileResults()) {
            PrecompileUtils.addContractCallResultToRecord(
                    this.gasRequirement,
                    childRecord,
                    result,
                    errorStatus,
                    dynamicProperties.shouldExportPrecompileResults(),
                    precompile.shouldAddTraceabilityFieldsToRecord(),
                    senderAddress,
                    provider.getRemainingGas(),
                    provider.getValue().toLong(),
                    provider.getInputData().toArrayUnsafe());
        }
    }

    private long defaultGas() {
        return dynamicProperties.htsDefaultGasCost();
    }

    @VisibleForTesting
    public Precompile getPrecompile() {
        return precompile;
    }
}
