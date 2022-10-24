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
package com.hedera.services.state.logic;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.InProgressChildRecord;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Collections;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SigsAndPayerKeyScreen {
    private static final Logger log = LogManager.getLogger(SigsAndPayerKeyScreen.class);

    private final Rationalization rationalization;
    private final PayerSigValidity payerSigValidity;
    private final MiscSpeedometers speedometers;
    private final TransactionContext txnCtx;
    private final BiPredicate<JKey, TransactionSignature> validityTest;
    private final Supplier<AccountStorageAdapter> accounts;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;
    private final AliasManager aliasManager;
    private EntityCreator creator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private SigImpactHistorian sigImpactHistorian;
    private RecordsHistorian recordsHistorian;

    @Inject
    public SigsAndPayerKeyScreen(
            final Rationalization rationalization,
            final PayerSigValidity payerSigValidity,
            final TransactionContext txnCtx,
            final MiscSpeedometers speedometers,
            final BiPredicate<JKey, TransactionSignature> validityTest,
            final Supplier<AccountStorageAdapter> accounts,
            ExpandHandleSpanMapAccessor spanMapAccessor,
            AliasManager aliasManager,
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            SigImpactHistorian sigImpactHistorian,
            RecordsHistorian recordsHistorian) {
        this.txnCtx = txnCtx;
        this.validityTest = validityTest;
        this.speedometers = speedometers;
        this.rationalization = rationalization;
        this.payerSigValidity = payerSigValidity;
        this.accounts = accounts;
        this.spanMapAccessor = spanMapAccessor;
        this.aliasManager = aliasManager;
        this.creator = creator;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordsHistorian = recordsHistorian;
    }

    public ResponseCodeEnum applyTo(SwirldsTxnAccessor accessor) {
        rationalization.performFor(accessor);

        final var sigStatus = rationalization.finalStatus();
        if (sigStatus == OK && rationalization.usedSyncVerification()) {
            speedometers.cycleSyncVerifications();
        }

        final var sigMeta = accessor.getSigMeta();
        sigMeta.replacePayerHollowKeyIfNeeded(accessor.getSigMap());

        if (hasActivePayerSig(accessor)) {
            txnCtx.payerSigIsKnownActive();
            if (sigMeta.hasReplacedHollowKey()) {
                try {
                    accounts.get()
                            .getForModify(EntityNum.fromAccountId(txnCtx.activePayer()))
                            .setAccountKey(sigMeta.payerKey());

                    final var accountKey = JKey.mapJKey(sigMeta.payerKey());
                    var syntheticUpdate =
                            syntheticTxnFactory.updateHollowAccount(
                                    EntityNum.fromAccountId(txnCtx.activePayer()), accountKey);
                    final var sideEffects = new SideEffectsTracker();
                    sideEffects.trackHollowAccountUpdate(txnCtx.activePayer());
                    final var childRecordBuilder =
                            creator.createSuccessfulSyntheticRecord(
                                    Collections.emptyList(), sideEffects, "lazy-create completion");
                    final var inProgress =
                            new InProgressChildRecord(
                                    DEFAULT_SOURCE_ID,
                                    syntheticUpdate,
                                    childRecordBuilder,
                                    Collections.emptyList());

                    final var childRecord = inProgress.recordBuilder();
                    sigImpactHistorian.markEntityChanged(
                            childRecord.getReceiptBuilder().getAccountId().num());
                    recordsHistorian.trackPrecedingChildRecord(
                            DEFAULT_SOURCE_ID, inProgress.syntheticBody(), childRecord);
                } catch (DecoderException e) {
                    // not able to map from JKey to Key
                }
            }
        }

        // TODO: figure out where the finalization of a hollow ethereum tranasction sender
        //  should be..
        //            final var ethTxExpansion = spanMapAccessor.getEthTxExpansion(accessor);
        //            if (ethTxExpansion != null && ethTxExpansion.result().equals(OK)) {
        //                replaceEthSenderKeyIfNecessary(accessor);
        //                // TODO: track preceding CryptoUpdate
        //            }

        return sigStatus;
    }

    //    private void replaceEthSenderKeyIfNecessary(SwirldsTxnAccessor accessor) {
    //        final var ethTxSigs = spanMapAccessor.getEthTxSigsMeta(accessor);
    //        final var callerNum = aliasManager.lookupIdBy(wrapUnsafely(ethTxSigs.address()));
    //        if (callerNum != EntityNum.MISSING_NUM) {
    //            final MerkleAccount account = accounts.get().getForModify(callerNum);
    //            if (account.getAccountKey() == null) {
    //                account.setAccountKey(new JECDSASecp256k1Key(ethTxSigs.publicKey()));
    //                accessor.getSigMeta().newHollowReplaced();
    //            }
    //        }
    //    }

    private boolean hasActivePayerSig(SwirldsTxnAccessor accessor) {
        try {
            return payerSigValidity.test(accessor, validityTest);
        } catch (Exception unknown) {
            log.warn("Unhandled exception while testing payer sig activation", unknown);
        }
        return false;
    }
}
