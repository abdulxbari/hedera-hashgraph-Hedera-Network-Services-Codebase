/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoTransfer}.
 */
public class CryptoTransferHandler implements TransactionHandler {

    /**
     * Validates a {@link com.hederahashgraph.api.proto.java.CryptoTransfer} that is part of a
     * {@link com.hederahashgraph.api.proto.java.Query}.
     *
     * @param txn the {@link TransactionBody} of the {@code CryptoTransfer}
     * @throws PreCheckException if validation fails
     */
    public void validate(@NonNull final TransactionBody txn) throws PreCheckException {
        // TODO: Migrate validation from CryptoTransferTransistionLogic.validateSemantics()
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoTransfer}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @param keyLookup the {@link AccountKeyLookup} to use to resolve keys
     * @param tokenStore the {@link ReadableTokenStore} to use to resolve token metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PrehandleHandlerContext context,
            @NonNull final AccountKeyLookup keyLookup,
            @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(context);
        requireNonNull(keyLookup);
        requireNonNull(tokenStore);
        final var op = context.getTxn().getCryptoTransfer();
        for (final var transfers : op.getTokenTransfersList()) {
            final var tokenMeta = tokenStore.getTokenMeta(transfers.getToken());
            if (!tokenMeta.failed()) {
                handleTokenTransfers(transfers.getTransfersList(), context, keyLookup);
                handleNftTransfers(
                        transfers.getNftTransfersList(), context, tokenMeta, op, keyLookup);
            } else {
                context.status(tokenMeta.failureReason());
            }
        }
        handleHbarTransfers(op, context, keyLookup);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }

    private void handleTokenTransfers(
            final List<AccountAmount> transfers,
            final PrehandleHandlerContext meta,
            final AccountKeyLookup keyLookup) {
        for (AccountAmount accountAmount : transfers) {
            final var keyOrFailure = keyLookup.getKey(accountAmount.getAccountID());
            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.getAmount() < 0 && !accountAmount.getIsApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.getAccountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.getAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.getAmount() > 0L;
                final var isMissingAcc =
                        isCredit
                                && keyOrFailure.failureReason().equals(INVALID_ACCOUNT_ID)
                                && isAlias(accountAmount.getAccountID());
                if (!isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private void handleNftTransfers(
            final List<NftTransfer> nftTransfersList,
            final PrehandleHandlerContext meta,
            final ReadableTokenStore.TokenMetaOrLookupFailureReason tokenMeta,
            final CryptoTransferTransactionBody op,
            final AccountKeyLookup keyLookup) {
        for (final var nftTransfer : nftTransfersList) {
            final var senderKeyOrFailure = keyLookup.getKey(nftTransfer.getSenderAccountID());
            if (!senderKeyOrFailure.failed()) {
                if (!nftTransfer.getIsApproval()) {
                    meta.addNonPayerKey(nftTransfer.getSenderAccountID());
                }
            } else {
                meta.status(senderKeyOrFailure.failureReason());
            }

            final var receiverKeyOrFailure =
                    keyLookup.getKeyIfReceiverSigRequired(nftTransfer.getReceiverAccountID());
            if (!receiverKeyOrFailure.failed()) {
                if (!receiverKeyOrFailure.equals(
                        KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED)) {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            nftTransfer.getReceiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                } else if (tokenMeta.metadata().hasRoyaltyWithFallback()
                        && !receivesFungibleValue(nftTransfer.getSenderAccountID(), op)) {
                    // Fallback situation; but we still need to check if the treasury is
                    // the sender or receiver, since in neither case will the fallback
                    // fee actually be charged
                    final var treasury = tokenMeta.metadata().treasury().toGrpcAccountId();
                    if (!treasury.equals(nftTransfer.getSenderAccountID())
                            && !treasury.equals(nftTransfer.getReceiverAccountID())) {
                        meta.addNonPayerKey(nftTransfer.getReceiverAccountID());
                    }
                }
            } else {
                final var isMissingAcc =
                        INVALID_ACCOUNT_ID.equals(receiverKeyOrFailure.failureReason())
                                && isAlias(nftTransfer.getReceiverAccountID());
                if (!isMissingAcc) {
                    meta.status(receiverKeyOrFailure.failureReason());
                }
            }
        }
    }

    private void handleHbarTransfers(
            final CryptoTransferTransactionBody op,
            final PrehandleHandlerContext meta,
            final AccountKeyLookup keyLookup) {
        for (AccountAmount accountAmount : op.getTransfers().getAccountAmountsList()) {
            final var keyOrFailure = keyLookup.getKey(accountAmount.getAccountID());

            if (!keyOrFailure.failed()) {
                final var isUnapprovedDebit =
                        accountAmount.getAmount() < 0 && !accountAmount.getIsApproval();
                if (isUnapprovedDebit) {
                    meta.addNonPayerKey(accountAmount.getAccountID());
                } else {
                    meta.addNonPayerKeyIfReceiverSigRequired(
                            accountAmount.getAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                }
            } else {
                final var isCredit = accountAmount.getAmount() > 0L;
                final var isImmutableAcc =
                        isCredit && keyOrFailure.failureReason().equals(ALIAS_IS_IMMUTABLE);
                final var isMissingAcc =
                        isCredit
                                && keyOrFailure.failureReason().equals(INVALID_ACCOUNT_ID)
                                && isAlias(accountAmount.getAccountID());
                if (!isImmutableAcc && !isMissingAcc) {
                    meta.status(keyOrFailure.failureReason());
                }
            }
        }
    }

    private boolean receivesFungibleValue(
            final AccountID target, final CryptoTransferTransactionBody op) {
        for (var adjust : op.getTransfers().getAccountAmountsList()) {
            if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
                return true;
            }
        }
        for (var transfers : op.getTokenTransfersList()) {
            for (var adjust : transfers.getTransfersList()) {
                if (adjust.getAmount() > 0 && adjust.getAccountID().equals(target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
