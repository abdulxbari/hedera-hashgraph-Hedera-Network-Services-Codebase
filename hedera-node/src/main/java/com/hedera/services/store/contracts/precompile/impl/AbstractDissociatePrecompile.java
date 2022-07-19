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
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.PrecompileInfoProvider;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import javax.inject.Provider;

public abstract class AbstractDissociatePrecompile implements Precompile {
    private final WorldLedgers ledgers;
    private final EvmSigsVerifier sigsVerifier;
    private final SideEffectsTracker sideEffects;
    private final InfrastructureFactory infrastructureFactory;
    protected final PrecompilePricingUtils pricingUtils;
    protected TransactionBody.Builder transactionBody;
    protected Dissociation dissociateOp;
    protected final DecodingFacade decoder;
    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final Provider<FeeCalculator> feeCalculator;
    protected final StateView currentView;

    protected AbstractDissociatePrecompile(
            final WorldLedgers ledgers,
            final DecodingFacade decoder,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffects,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils,
            final Provider<FeeCalculator> feeCalculator,
            final StateView currentView) {
        this.ledgers = ledgers;
        this.sigsVerifier = sigsVerifier;
        this.sideEffects = sideEffects;
        this.infrastructureFactory = infrastructureFactory;
        this.pricingUtils = pricingUtils;
        this.decoder = decoder;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.feeCalculator = feeCalculator;
        this.currentView = currentView;
    }

    @Override
    public void run(final PrecompileInfoProvider provider) {
        Objects.requireNonNull(dissociateOp);

        /* --- Check required signatures --- */
        final var accountId = Id.fromGrpcAccount(dissociateOp.accountId());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        provider,
                        accountId.asEvmAddress(),
                        sigsVerifier::hasActiveKey,
                        ledgers,
                        provider.aliases());
        validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());

        /* --- Execute the transaction and capture its results --- */
        final var dissociateLogic =
                infrastructureFactory.newDissociateLogic(accountStore, tokenStore);
        final var validity = dissociateLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);
        dissociateLogic.dissociate(accountId, dissociateOp.tokenIds());
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(DISSOCIATE, consensusTime);
    }
}
