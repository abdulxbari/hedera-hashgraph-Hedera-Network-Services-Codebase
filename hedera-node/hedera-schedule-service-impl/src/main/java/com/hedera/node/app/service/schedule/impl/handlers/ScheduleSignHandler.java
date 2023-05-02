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
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ReadableScheduleStore.ScheduleMetadata;
import com.hedera.node.app.service.schedule.impl.ScheduleUtility.isValidSignature;
import com.hedera.node.app.service.schedule.impl.WritableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.SchedulingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_SIGN}.
 */
@Singleton
@SuppressWarnings("OverlyCoupledClass")
public class ScheduleSignHandler extends AbstractScheduleHandler implements TransactionHandler {
    private static final Logger logger = LogManager.getLogger(ScheduleSignHandler.class);

    @Inject
    public ScheduleSignHandler() {
    }

    @Override
    public void pureChecks(@Nullable final TransactionBody currentTransaction) throws PreCheckException {
        getValidScheduleSignBody(currentTransaction);
    }

    @NonNull
    private ScheduleSignTransactionBody getValidScheduleSignBody(@Nullable final TransactionBody currentTransaction)
            throws PreCheckException {
        if(currentTransaction != null) {
            final ScheduleSignTransactionBody scheduleSignTransaction = currentTransaction.scheduleSign();
            if (scheduleSignTransaction != null) {
                if (scheduleSignTransaction.scheduleID() != null) {
                    return scheduleSignTransaction;
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION);
        }
    }

    /**
     * Pre-handles a {@link HederaFunctionality#SCHEDULE_SIGN} transaction, returning the metadata
     * required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException if the transaction cannot be handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        final ReadableScheduleStore scheduleStore = context.createStore(ReadableScheduleStore.class);
        final SchedulingConfig schedulingConfig = context.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody currentTransaction = context.body();
        final ScheduleSignTransactionBody scheduleSignTransaction = getValidScheduleSignBody(currentTransaction);
        if (scheduleSignTransaction != null && scheduleSignTransaction.scheduleID() != null) {
            final ScheduleMetadata scheduleData =
                    preValidate(scheduleStore, isLongTermEnabled, scheduleSignTransaction.scheduleID());
            final Key adminKey = scheduleData.adminKey();
            if(adminKey != null)
                context.requireKey(adminKey);
            else
                throw new PreCheckException(ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE);
            final AccountID payerAccount = scheduleData.designatedPayer().orElse(null);
            if (payerAccount != null) {
               final ReadableAccountStore accountStore = context.createStore(ReadableAccountStore.class);
               final Account payer = accountStore.getAccountById(payerAccount);
               if (payer != null) {
                  final Key payerKey = payer.key();
                  if(payerKey != null)
                      context.requireKey(payerKey);
               }
            }
            // Need to do the following:
            //    Get the keys required for the child transaction (requires parsing, the "signatories" and "notary" are the already validated keys, not the full required list)
            //      Note: Can we store the required list in state when the schedule is created?
            //            -- No, because accounts must sign, and the required primitive keys for an account may change.
            //               between create and execute.
            //               We must re-process the child on every create/sign until the primitive keys that have signed matches
            //               the current required set.
            //    Add all required keys to the optional keys on context
            //    ALSO need to add the existing set of *primitive* keys that have already signed
            final TransactionBody schedulableTransaction = scheduleData.scheduledTxn();
            try {
                final Set<Key> allKeysNeeded = allKeysForTransaction(schedulableTransaction,
                        scheduleData.signatories());
                context.optionalKeys(allKeysNeeded);
            } catch (HandleException translated) {
                throw new PreCheckException(translated.getStatus());
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
        // context now has all of the keys required by the scheduled transaction in optional keys
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws HandleException if the transaction is not handled successfully.
     *     The response code appropriate to the failure reason will be provided via this exception.
     */
    @Override
    public void handle(@NonNull final HandleContext transactionContext) throws HandleException {
        WritableScheduleStoreImpl scheduleStore = transactionContext.writableStore(WritableScheduleStoreImpl.class);
        final SchedulingConfig schedulingConfig = transactionContext.configuration().getConfigData(SchedulingConfig.class);
        final boolean isLongTermEnabled = schedulingConfig.longTermEnabled();
        final TransactionBody originalTransactionBody = transactionContext.body();
        if (originalTransactionBody.hasScheduleSign()) {
            final ScheduleSignTransactionBody signTransaction = originalTransactionBody.scheduleSignOrThrow();
            final ScheduleID idToSign = signTransaction.scheduleID();
            final ScheduleMetadata scheduleData = scheduleStore.get(idToSign).get();
            final ResponseCodeEnum validationResult =
                validate(scheduleData, transactionContext.consensusNow(), isLongTermEnabled);
            if(validationResult == ResponseCodeEnum.OK) {
                final ScheduleVirtualValue scheduleToSign = scheduleStore.getVirtualValueForModify(idToSign); // method name is temporary until schedule PBJ objects exist and store interface is updated
                if (scheduleToSign != null) {
                    final SchedulableTransactionBody schedulableTransaction = fromPbj(scheduleToSign.scheduledTxn());
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
        } else {
            throw new HandleException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        }
    }

    // Ostensibly convert from com.hederahashgraph.api.proto.java.SchedulableTransactionBody to
    // com.hedera.hapi.node.scheduled.SchedulableTransactionBody, but the former isn't actually
    // visible (and should not be), and we shouldn't be using ScheduleVirtualValue because it
    // doesn't store Keys in a manner usable with the new "Signature Verification" code
    // so just stub this out for now.
    private SchedulableTransactionBody fromPbj(final Object scheduledTxn) {
        final String message = """
            Method is Not implemented.
            We require PBJ Schedule and ScheduledTransaction objects as an alternative to using obsolete
            ScheduleVirtualValue and converting here.
            This is temporarily present only to stub out required functionality.
            """;
        throw new UnsupportedOperationException(message);
    }

}
