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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Account.Builder;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody.StakedIdOneOfType;
import com.hedera.node.app.service.mono.exceptions.InsufficientFundsException;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_CREATE}.
 */
@Singleton
public class CryptoCreateHandler implements TransactionHandler {
    private static final Logger log = LogManager.getLogger(CryptoCreateHandler.class);

    @Inject
    public CryptoCreateHandler() {
        // Exists for injection
    }

    /**
     * Pre-handles a {@link HederaFunctionality#CRYPTO_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        final var op = context.getTxn().cryptoCreateAccountOrThrow();
        if (op.hasKey()) {
            final var key = asHederaKey(op.keyOrThrow());
            final var receiverSigReq = op.receiverSigRequired();
            if (receiverSigReq && key.isPresent()) {
                context.addToReqNonPayerKeys(key.get());
            }
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final TransactionBody txnBody,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final CryptoCreateRecordBuilder recordBuilder,
            @NonNull final boolean areCreatableAccounts) {
        final var op = txnBody.cryptoCreateAccount();
        try {
            // If accounts can't be created, due to the usage of a price regime, throw an exception
            if (!areCreatableAccounts) {
                throw new HandleException(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            }

            // validate payer account exists and has enough balance
            final var optionalPayer =
                    accountStore.getForModify(
                            txnBody.transactionIDOrElse(TransactionID.DEFAULT)
                                    .accountIDOrElse(AccountID.DEFAULT));
            final long newPayerBalance = optionalPayer.get().tinybarBalance() - op.initialBalance();
            validatePayer(optionalPayer, newPayerBalance);

            final var modifiedPayer =
                    optionalPayer.get().copyBuilder().tinybarBalance(newPayerBalance).build();
            accountStore.put(modifiedPayer);

            // Build the account to be persisted based on the transaction body
            final var accountCreated = buildAccount(op, handleContext);
            accountStore.put(accountCreated);

            recordBuilder.setCreatedAccount(accountCreated.accountNumber());

            if (op.alias() != Bytes.EMPTY) {
                aliasState.put(op.alias(), EntityNum.fromAccount(created));
            }
        } catch (InsufficientFundsException ife) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            throw new HandleException(FAIL_INVALID);
        }
    }

    private void validatePayer(
            @NonNull final Optional<Account> optionalPayer, final long initialBalance) {
        final var payerAccount = optionalPayer.get();
        // If the payer account is deleted, throw an exception
        if (payerAccount.deleted()) {
            throw new HandleException(ACCOUNT_DELETED);
        }
        // TODO: check if payer account is detached when we have started expiring accounts ?
        final long newPayerBalance = payerAccount.tinybarBalance() - initialBalance;
        if (newPayerBalance < 0) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        }
    }

    private Account buildAccount(CryptoCreateTransactionBody op, HandleContext handleContext) {
        long autoRenewPeriod = op.autoRenewPeriod().seconds();
        long consensusTime = handleContext.consensusNow().getEpochSecond();
        long expiry = consensusTime + autoRenewPeriod;
        var builder =
                Account.newBuilder()
                        .memo(op.memo())
                        .expiry(expiry)
                        .autoRenewSecs(autoRenewPeriod)
                        .receiverSigRequired(op.receiverSigRequired())
                        .maxAutoAssociations(op.maxAutomaticTokenAssociations())
                        .declineReward(op.declineReward());

        if (onlyKeyProvided(op)) {
            builder.key(op.key());
        } else if (keyAndAliasProvided(op)) {
            builder.key(op.key()).alias(op.alias());
        }

        if (op.hasStakedAccountId() || op.hasStakedNodeId()) {
            final var stakeNumber =
                    getStakedId(op.stakedId().kind(), op.stakedAccountId(), op.stakedNodeId());
            builder.stakedNumber(stakeNumber);
        }
        builder.accountNumber(handleContext.newEntityNumSupplier().getAsLong());
        return builder.build();
    }

    private boolean onlyKeyProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && op.getAlias().isEmpty();
    }

    private boolean keyAndAliasProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.getAlias().isEmpty();
    }

    private boolean onlyAliasProvided(@NonNull final CryptoCreateTransactionBody op) {
        return !op.hasKey() && !op.getAlias().isEmpty();
    }

    /**
     * Gets the stakedId from the provided staked_account_id or staked_node_id.
     *
     * @param stakedAccountId given staked_account_id
     * @param stakedNodeId given staked_node_id
     * @return valid staked id
     */
    private long getStakedId(
            final StakedIdOneOfType idCase,
            final AccountID stakedAccountId,
            final long stakedNodeId) {
        if (idCase.equals(StakedIdOneOfType.STAKED_ACCOUNT_ID)) {
            return stakedAccountId.accountNum();
        } else {
            // return a number less than the given node Id, in order to recognize the if nodeId 0 is
            // set
            return -stakedNodeId - 1;
        }
    }
}
