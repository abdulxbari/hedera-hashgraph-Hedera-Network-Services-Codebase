/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore.ScheduleMetadata;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Basic utility class for Schedule Handlers.
 */
public final class ScheduleUtility {
    private ScheduleUtility() {}

    @NonNull
    public static TransactionBody asOrdinary(@NonNull final SchedulableTransactionBody scheduledTxn,
            @NonNull final TransactionID scheduledTxnTransactionId) throws UnknownHederaFunctionality {
        final TransactionBody.Builder ordinary = TransactionBody.newBuilder();
        ordinary.transactionFee(scheduledTxn.transactionFee())
                .memo(scheduledTxn.memo())
                .transactionID(scheduledTxnTransactionId.copyBuilder().scheduled(true));

        switch (scheduledTxn.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> ordinary.consensusCreateTopic(scheduledTxn.consensusCreateTopicOrThrow());
            case CONSENSUS_UPDATE_TOPIC -> ordinary.consensusUpdateTopic(scheduledTxn.consensusUpdateTopicOrThrow());
            case CONSENSUS_DELETE_TOPIC -> ordinary.consensusDeleteTopic(scheduledTxn.consensusDeleteTopicOrThrow());
            case CONSENSUS_SUBMIT_MESSAGE -> ordinary.consensusSubmitMessage(
                    scheduledTxn.consensusSubmitMessageOrThrow());
            case CRYPTO_CREATE_ACCOUNT -> ordinary.cryptoCreateAccount(scheduledTxn.cryptoCreateAccountOrThrow());
            case CRYPTO_UPDATE_ACCOUNT -> ordinary.cryptoUpdateAccount(scheduledTxn.cryptoUpdateAccountOrThrow());
            case CRYPTO_TRANSFER -> ordinary.cryptoTransfer(scheduledTxn.cryptoTransferOrThrow());
            case CRYPTO_DELETE -> ordinary.cryptoDelete(scheduledTxn.cryptoDeleteOrThrow());
            case FILE_CREATE -> ordinary.fileCreate(scheduledTxn.fileCreateOrThrow());
            case FILE_APPEND -> ordinary.fileAppend(scheduledTxn.fileAppendOrThrow());
            case FILE_UPDATE -> ordinary.fileUpdate(scheduledTxn.fileUpdateOrThrow());
            case FILE_DELETE -> ordinary.fileDelete(scheduledTxn.fileDeleteOrThrow());
            case CONTRACT_CREATE_INSTANCE -> ordinary.contractCreateInstance(
                    scheduledTxn.contractCreateInstanceOrThrow());
            case CONTRACT_UPDATE_INSTANCE -> ordinary.contractUpdateInstance(
                    scheduledTxn.contractUpdateInstanceOrThrow());
            case CONTRACT_CALL -> ordinary.contractCall(scheduledTxn.contractCallOrThrow());
            case CONTRACT_DELETE_INSTANCE -> ordinary.contractDeleteInstance(
                    scheduledTxn.contractDeleteInstanceOrThrow());
            case SYSTEM_DELETE -> ordinary.systemDelete(scheduledTxn.systemDeleteOrThrow());
            case SYSTEM_UNDELETE -> ordinary.systemUndelete(scheduledTxn.systemUndeleteOrThrow());
            case FREEZE -> ordinary.freeze(scheduledTxn.freezeOrThrow());
            case TOKEN_CREATION -> ordinary.tokenCreation(scheduledTxn.tokenCreationOrThrow());
            case TOKEN_FREEZE -> ordinary.tokenFreeze(scheduledTxn.tokenFreezeOrThrow());
            case TOKEN_UNFREEZE -> ordinary.tokenUnfreeze(scheduledTxn.tokenUnfreezeOrThrow());
            case TOKEN_GRANT_KYC -> ordinary.tokenGrantKyc(scheduledTxn.tokenGrantKycOrThrow());
            case TOKEN_REVOKE_KYC -> ordinary.tokenRevokeKyc(scheduledTxn.tokenRevokeKycOrThrow());
            case TOKEN_DELETION -> ordinary.tokenDeletion(scheduledTxn.tokenDeletionOrThrow());
            case TOKEN_UPDATE -> ordinary.tokenUpdate(scheduledTxn.tokenUpdateOrThrow());
            case TOKEN_MINT -> ordinary.tokenMint(scheduledTxn.tokenMintOrThrow());
            case TOKEN_BURN -> ordinary.tokenBurn(scheduledTxn.tokenBurnOrThrow());
            case TOKEN_WIPE -> ordinary.tokenWipe(scheduledTxn.tokenWipeOrThrow());
            case TOKEN_ASSOCIATE -> ordinary.tokenAssociate(scheduledTxn.tokenAssociateOrThrow());
            case TOKEN_DISSOCIATE -> ordinary.tokenDissociate(scheduledTxn.tokenDissociateOrThrow());
            case SCHEDULE_DELETE -> ordinary.scheduleDelete(scheduledTxn.scheduleDeleteOrThrow());
            case TOKEN_PAUSE -> ordinary.tokenPause(scheduledTxn.tokenPauseOrThrow());
            case TOKEN_UNPAUSE -> ordinary.tokenUnpause(scheduledTxn.tokenUnpauseOrThrow());
            case CRYPTO_APPROVE_ALLOWANCE -> ordinary.cryptoApproveAllowance(
                    scheduledTxn.cryptoApproveAllowanceOrThrow());
            case CRYPTO_DELETE_ALLOWANCE -> ordinary.cryptoDeleteAllowance(scheduledTxn.cryptoDeleteAllowanceOrThrow());
            case TOKEN_FEE_SCHEDULE_UPDATE -> ordinary.tokenFeeScheduleUpdate(
                    scheduledTxn.tokenFeeScheduleUpdateOrThrow());
            case UTIL_PRNG -> ordinary.utilPrng(scheduledTxn.utilPrngOrThrow());
        }
        return ordinary.build();
    }

    @NonNull
    public static ScheduleID toPbj(com.hederahashgraph.api.proto.java.ScheduleID valueToConvert) {
        return new ScheduleID(valueToConvert.getShardNum(), valueToConvert.getRealmNum(),
                valueToConvert.getScheduleNum());
    }

    @NonNull
    public static ScheduleMetadata createMetadata(@NonNull final ScheduleCreateTransactionBody createTransaction,
            @NonNull final TransactionID parentTransactionId, @NonNull final Instant currentConsensusTime)
            throws HandleException {
        Objects.requireNonNull(parentTransactionId);
        final SchedulableTransactionBody scheduledTransaction = createTransaction.scheduledTransactionBody();
        try {
            if (scheduledTransaction != null) {
                return new ScheduleMetadata(createTransaction.adminKey(),
                        ScheduleUtility.asOrdinary(scheduledTransaction, parentTransactionId),
                        Optional.ofNullable(createTransaction.payerAccountID()),
                        getSchedulingAccount(createTransaction), false, false,
                        calculateExpiration(createTransaction, currentConsensusTime),
                        Collections.emptySet());
            } else {
                throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
        } catch (final UnknownHederaFunctionality e) {
            throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    private static AccountID getSchedulingAccount(final ScheduleCreateTransactionBody createTransaction) {
        return null;
    }

    private static Instant calculateExpiration(final ScheduleCreateTransactionBody createTransaction,
            @NonNull final Instant currentConsensusTime) {
        return null;
    }

    /**
     * Predicate that checks if a given key has been validated or verified.
     * The current implementation is naive.  We may need to include the set of pre-verified keys and
     * some information about partially verified keys in order to properly validate whether a given
     * key is fully active, partially active, or not active.
     */
    public static final class isValidSignature implements Predicate<Key> {
        private final HandleContext transactionContext;
        private final Set<Key> signatories;

        public isValidSignature(@NonNull final HandleContext transactionContext,
                @Nullable final Set<Key> signatories) {
            this.transactionContext = Objects.requireNonNull(transactionContext);
            this.signatories = signatories;
        }

        @Override
        public boolean test(final Key key) {
            /*
            final SignatureVerification verificationResult =
                    transactionContext.extendedVerificationFor(key, signatories);
             */
            // Not correct, need to supply added primitive keys as above
            final SignatureVerification verificationResult = transactionContext.verificationFor(key);
            return (verificationResult != null && verificationResult.passed());
        }
    }
}
