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

package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ReadableScheduleStore.ScheduleMetadata;
import com.hedera.node.app.service.schedule.impl.ScheduleUtility;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
abstract class AbstractScheduleHandler {
    protected static final String NULL_CONTEXT_MESSAGE =
            "Dispatcher called the schedule delete handler with a null context; probable internal data corruption.";
    protected static final String INCORRECT_TRANSACTION_TYPE_MESSAGE =
            "Invalid or incomplete transaction provided to Handle.  Expected %s, but got %s.";

    /**
     * Given a TransactionBody and a {@code Set<Key>} of <strong>primitive</strong> Key signatories, return a
     * {@code Set<Key>} of <strong>primitive</strong> Key objects still (potentially) required in order to
     * fully authorize (sign) the given transaction.
     * <p>
     * The signatories provided are all of the <strong>primitive</strong> Keys that have signed the transaction
     * previously, and may be null to indicate there are no previous signatories.  This method will process the
     * given transaction to determine what {@link Account}s must sign that transaction in order to be valid, and
     * find all of the primitive keys associated with those accounts (potentially handling key lists, subkeys,
     * threshold keys, etc...).  The method will then find the set difference between the account keys and the
     * signatories.  That Set (which may be empty) is then returned.
     * </p>
     * @param scheduledTransaction A Transaction that is currently scheduled for future execution, or is about to be
     *        scheduled for future execution.
     * @param signatories A Set of primitive keys that have already signed the scheduled transaction.  This may be
     *        empty, signifying that no primitive keys have yet validly signed the transaction, or null, signifying
     *        that signatories are not considered for this call (e.g. for a gatherSignatures call, where we just
     *        need all of the possible signatures, or when initially creating a new schedule).
     * @return a {@link Set} of primitive {@link Key} objects (each containing exactly one simple cryptographic key)
     *         that must sign the given transaction in order for that transaction to be considered authorized and
     *         ready to execute.
     * @throws HandleException If any characteristic of the scheduled transaction, or any element of state, prevents
     *         continued processing and warrants failing the current user transaction.
     */
    @NonNull
    protected Set<Key> allKeysForTransaction(@NonNull final TransactionBody scheduledTransaction,
            @Nullable final Set<Key> signatories) throws HandleException {
        Objects.requireNonNull(scheduledTransaction, "getAllKeysFromTransaction called with null scheduledTransaction");
        try {
            checkSchedulable(scheduledTransaction.data().kind());
            // @todo('Discussion') We need to call an API on Context to just get a list (Set?) of all keys
            //                     defined for the child transaction.  This API is not available currently.
            //                     The method must accept a Set of existing primitive keys (Bytes objects) that
            //                     is possibly null and contains the keys that have already signed, so that only
            //                     keys that have not already "signed" are returned.
        } catch(PreCheckException translated) {
            throw new HandleException(translated.responseCode());
        }
        return Collections.emptySet();
    }

    @NonNull
    protected Set<Key> allKeysForTransaction(@NonNull final SchedulableTransactionBody scheduledTransaction,
            @Nullable final Set<Key> signatories)
            throws HandleException {
        try {
            // The transaction ID doesn't matter, as we're reading the signature map from state in this case
            // (or should be).
            final TransactionID transactionId = TransactionID.newBuilder().build();
            // @todo('merge') This isn't quite right, what we need is some mechanism to get the
            //                keys that are *not yet verified*, which is probably "all keys" then
            //                subtract "previously verified keys", and return that.
            //                The ideal is that if every key in the returned list is newly verified,
            //                then this scheduled transaction is valid and may be executed.
            //                  Corners:
            //                      Partially verified threshold keys (some subkeys previous some possibly new)
            //                      Partially verified key lists
            //                      deep key forests with some primitive keys verified, some not
            //                      keys that may have changed between early verification and current?
            //                Basically we need a walk of the Key forest, and return all of the not-yet-verified
            //                Key objects (Not the primitive "Key" Bytes).
            return allKeysForTransaction(ScheduleUtility.asOrdinary(scheduledTransaction, transactionId), signatories);
        } catch (UnknownHederaFunctionality e) {
            throw new HandleException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }
    }
    /**
     * Determine if the transaction type could possibly be allowed to be scheduled.
     * Some types may not be in {@link SchedulableTransactionBody} yet but could be in the future.
     * The scheduling.whitelist configuration property is separate from this and provides the final
     * list of transaction types that can be scheduled.
     * @param transactionType any {@link TransactionBody.DataOneOfType} for a transaction to be scheduled.
     * @throws PreCheckException if the provided transaction type cannot be scheduled, the response code
     *     will be {@link ResponseCodeEnum#SCHEDULED_TRANSACTION_NOT_IN_WHITELIST}
     */
    private void checkSchedulable(@Nullable final TransactionBody.DataOneOfType transactionType)
            throws PreCheckException {
        Objects.requireNonNull(transactionType, "checkSchedulable called with null transactionType");
        switch (transactionType) {
            case SCHEDULE_CREATE, SCHEDULE_SIGN, UNSET:
                throw new PreCheckException(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
            default: /* do nothing here*/
                ;
        }
    }

    /**
     * Given a transaction body and schedule store, validate the transaction meets minimum requirements to
     * be completed.
     * <p>This method checks that a transaction exists, is a ScheduleSign, has a Schedule ID to sign,
     * the Schedule ID references a schedule in readable state, and the referenced schedule has a child transaction.
     * If all validation checks pass, the schedule metadata is returned.
     * If any checks fail, then a {@link PreCheckException} is thrown.</p>
     * @param idToValidate the ID of the schedule to validate
     * @param scheduleStore data from readable state which contains, at least, a metadata entry for the schedule
     *     that the current transaction will sign.
     * @throws PreCheckException if the ScheduleSign transaction provided fails any of the required validation
     *     checks.
     */
    @NonNull
    protected ScheduleMetadata preValidate(@NonNull final ReadableScheduleStore scheduleStore,
            final boolean isLongTermEnabled, @Nullable final ScheduleID idToValidate)
            throws PreCheckException {
        if (idToValidate != null) {
            final Optional<ScheduleMetadata> scheduleOptional = scheduleStore.get(idToValidate);
            if (scheduleOptional.isPresent()) {
                final ScheduleMetadata scheduleData = scheduleOptional.get();
                if (scheduleData.scheduledTxn() != null) {
                    final ResponseCodeEnum validationResult = validate(scheduleData, null, isLongTermEnabled);
                    if(validationResult == ResponseCodeEnum.OK) {
                        return scheduleData;
                    } else {
                      throw new PreCheckException(validationResult);
                    }
                } else {
                    throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
                }
            } else {
                throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            }
        } else {
            throw new PreCheckException(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        }
    }

    /**
     * Given a schedule metadata, consensus time, and long term scheduling enabled flag, validate the transaction
     * meets minimum requirements to be handled.
     *
     * @param scheduleToValidate the metadata to validate.  If this is null then
     *     {@link ResponseCodeEnum#INVALID_SCHEDULE_ID} is returned.
     * @param consensusTime the consensus time applicable to this transaction.  If this is null then we assume this is
     *     a pre-check and do not validate expiration.
     * @param isLongTermEnabled a flag indicating if long term scheduling is currently enabled.
     * @return a response code representing the result of the validation.  This is {@link ResponseCodeEnum#OK}
     *     if all checks pass, or an appropriate failure code if any checks fail.
     */
    @NonNull
    protected ResponseCodeEnum validate(@Nullable final ScheduleMetadata scheduleToValidate,
            @Nullable final Instant consensusTime, final boolean isLongTermEnabled) {
        final ResponseCodeEnum result;
        final Instant effectiveConsensusTime = Objects.requireNonNullElse(consensusTime, Instant.MIN);
        if(scheduleToValidate != null) {
            if (!scheduleToValidate.scheduleExecuted()) {
                if (!scheduleToValidate.scheduleDeleted()) {
                    if (effectiveConsensusTime.isBefore(scheduleToValidate.calculatedExpirationTime())) {
                        result = ResponseCodeEnum.OK;
                    } else {
                        if (isLongTermEnabled) {
                            result = ResponseCodeEnum.INVALID_SCHEDULE_ID;
                        } else {
                            result = ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
                        }
                    }
                } else {
                    result = ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
                }
            } else {
                result = ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
            }
        } else {
            result = ResponseCodeEnum.INVALID_SCHEDULE_ID;
        }
        return result;
    }

}
