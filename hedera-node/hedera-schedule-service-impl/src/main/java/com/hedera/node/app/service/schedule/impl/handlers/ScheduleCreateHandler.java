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

package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore.ScheduleMetadata;
import com.hedera.node.app.service.schedule.impl.ScheduleUtility;
import com.hedera.node.app.service.schedule.impl.ScheduleUtility.isValidSignature;
import com.hedera.node.app.service.schedule.impl.WritableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.SchedulingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_CREATE}.
 */
@Singleton
public class ScheduleCreateHandler extends AbstractScheduleHandler implements TransactionHandler {
    private static final Logger logger = LogManager.getLogger(ScheduleCreateHandler.class);

    @Inject
    public ScheduleCreateHandler() {
        super();
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        if(currentTransaction != null) {
            getSchedulableTransaction(currentTransaction);
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_CREATE} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required and optional signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if the transaction cannot be handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        Objects.requireNonNull(context);
        final TransactionBody currentTransaction = context.body();
        final TransactionBody schedulableTransaction = getSchedulableTransaction(currentTransaction);
        // Will never be null, so check is unnecessary, but Sonar doesn't detect that correctly.
        final ScheduleCreateTransactionBody scheduleBody = Objects.requireNonNull(currentTransaction.scheduleCreate());
        // If we have a payer account, add it to required keys.
        // Note, the context forces payer from transaction ID as txn.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT),
        //       Need to figure out if we use that, if we need a way to override, or if this is payer for the transaction being scheduled.
        if(scheduleBody.hasPayerAccountID()) {
            final AccountID payerForSchedule = scheduleBody.payerAccountIDOrThrow();
            final Key payerKey = getKeyForAccount(payerForSchedule, context.createStore(ReadableAccountStore.class));
            if (payerKey != null)
                context.requireKey(payerKey);
            else
                throw new PreCheckException(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        }
        if(scheduleBody.hasAdminKey()) {
            // Create may have an admin key, but it is not required
            // If there is no admin key, however, the schedule becomes immutable, and cannot be signed.
            // If there is no admin key, therefore, all signatures must be present on create
            context.requireKey(scheduleBody.adminKeyOrThrow());
        }
        final TransactionID transactionId = currentTransaction.transactionID();
        if (transactionId != null) {
            if (schedulableTransaction != null) {
                context.optionalKeys(allKeysForTransaction(schedulableTransaction, null));
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
            // context now has all of the keys required by the scheduled transaction in optional keys
//            // There must be an admin key in the transaction (not metadata) and it must sign.
//            // Note also that the metadata doesn't exist yet, so we do not read state for this transaction
//            //      except to get a new Schedule ID (need to look up how to do that).
//            //      Also, need to check if there is a duplicate schedule already present.
//            //  Goal: Create the schedule in state, then check signature verification.
//            //        If all signatures have signed, execute the transaction (dispatch?)
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    @Nullable
    private Key getKeyForAccount(@NonNull final AccountID accountToQuery,
            @NonNull final ReadableAccountStore accountStore) throws PreCheckException {
        Objects.requireNonNull(accountToQuery);
        final Account accountData = accountStore.getAccountById(accountToQuery);
        if(accountData != null)
          return accountData.key();
        else
          throw new PreCheckException(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @throws HandleException if the transaction is not handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void handle(@NonNull final HandleContext transactionContext) throws HandleException {
        final Instant currentConsensusTime = transactionContext.consensusNow();
        final WritableScheduleStoreImpl scheduleStore =
                transactionContext.writableStore(WritableScheduleStoreImpl.class);
        final SchedulingConfig schedulingConfig =
                transactionContext.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        /* @todo('merge') We need to always store the custom payer in the schedule object.
                          It is part of the "other" parties, and we need to know to store it's key with the schedule
                          in all cases. This fixes a case where the ScheduleCreate payer and the custom payer are the
                          same payer, which would cause the custom payers signature to not get stored and then a
                          ScheduleSign would not execute the transaction without an extra signature from
                          the custom payer. */
        final TransactionBody originalTransactionBody = transactionContext.body();
        if (originalTransactionBody.hasScheduleCreate()) {
            final ScheduleCreateTransactionBody createTransaction = originalTransactionBody.scheduleCreateOrThrow();
            final ScheduleMetadata scheduleData =
                    ScheduleUtility.createMetadata(createTransaction, originalTransactionBody.transactionIDOrThrow(),
                            currentConsensusTime);
            final ResponseCodeEnum validationResult =
                validate(scheduleData, currentConsensusTime, isLongTermEnabled);
            if(validationResult == ResponseCodeEnum.OK) {
                final SchedulableTransactionBody schedulableTransaction =
                        createTransaction.scheduledTransactionBodyOrThrow();
                // Need to process the child transaction again, to get the *primitive* keys possibly required
                final Set<Key> allRequiredKeys =
                        allKeysForTransaction(schedulableTransaction, scheduleData.signatories());
                // The following stream is probably naive.  We likely need to pre-filter the keys by
                // removing all keys already known to be active for the schedule, and even then we likely need
                // some form of forest-walk to work out if partially valid (e.g.) threshold keys are completed
                // with the newly validated keys.
                // Also, move this to util, so it can be used in ScheduleCreate (which might execute).
                if(allRequiredKeys.parallelStream()
                        .allMatch(new isValidSignature(transactionContext, scheduleData.signatories()))) {
                // @todo('next') Create the new Schedule object (even if it executes, we still have to store it
                //               marked as "executed", so fees, receipts, record stream, etc... are all correct).
                // @todo('next') all of the keys are signature verified, we may be able to execute
                //                 If not long-term, and execute enabled,
                //                     dispatch the scheduled transaction and mark as executed
                //               commit the changes to the state?
                }
            } else {
                throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            }
        } else {
            throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        }
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    private TransactionBody getSchedulableTransaction(@NonNull final TransactionBody currentTransaction)
            throws PreCheckException {
        final ScheduleCreateTransactionBody scheduleBody = currentTransaction.scheduleCreate();
        if(scheduleBody != null) {
            final SchedulableTransactionBody scheduledTransaction = scheduleBody.scheduledTransactionBody();
            final TransactionID transactionId = currentTransaction.transactionID();
            if (scheduledTransaction != null && transactionId != null) {
                final AccountID transactionAccountId = transactionId.accountID();
                try {
                    if(transactionAccountId != null)
                        return ScheduleUtility.asOrdinary(scheduledTransaction, transactionId);
                    else
                        throw new PreCheckException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
                } catch (UnknownHederaFunctionality ignored) {
                    throw new PreCheckException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }
}
