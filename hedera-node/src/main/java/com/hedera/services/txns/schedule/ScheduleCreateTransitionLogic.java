package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.txns.validation.PureValidation;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class ScheduleCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ScheduleCreateTransitionLogic.class);

	private static final EnumSet<ResponseCodeEnum> ACCEPTABLE_SIGNING_OUTCOMES = EnumSet.of(OK,
			NO_NEW_VALID_SIGNATURES);

	private final OptionValidator validator;
	private final InHandleActivationHelper activationHelper;

	private final ScheduleExecutor executor;
	private final ScheduleStore store;
	private final TransactionContext txnCtx;

	SigMapScheduleClassifier classifier = new SigMapScheduleClassifier();
	SignatoryUtils.ScheduledSigningsWitness signingsWitness = SignatoryUtils::witnessScoped;

	@Inject
	public ScheduleCreateTransitionLogic(
			ScheduleStore store,
			TransactionContext txnCtx,
			InHandleActivationHelper activationHelper,
			OptionValidator validator,
			ScheduleExecutor executor
	) {
		this.store = store;
		this.txnCtx = txnCtx;
		this.activationHelper = activationHelper;
		this.validator = validator;
		this.executor = executor;
	}

	@Override
	public void doStateTransition() {
		try {
			final var accessor = txnCtx.accessor();
			transitionFor(accessor.getTxnBytes(), accessor.getSigMap());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxnWrapper(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(final byte[] bodyBytes, final SignatureMap sigMap) throws InvalidProtocolBufferException {
		final var idSchedulePair = store.lookupSchedule(bodyBytes);
		@Nullable final var existingScheduleId = idSchedulePair.getLeft();
		final var schedule = idSchedulePair.getRight();
		if (null != existingScheduleId) {
			/* TODO : if the flag merge_with_identical_schedule is false */
			completeContextWith(existingScheduleId, schedule, IDENTICAL_SCHEDULE_ALREADY_CREATED);
			return;
			/* TODO : if the flag merge_with_identical_schedule is true
			if (txnCtx.accessor().getTxn().getScheduleCreate().hasPayerAccountID() &&
					schedule.payer().toGrpcAccountId() ==
							txnCtx.accessor().getTxn().getScheduleCreate().getPayerAccountID()) {
				perform a schedule sign with any signatures from this txn and return with success.
			} else {
				completeContextWith(existingScheduleId, schedule, IDENTICAL_SCHEDULE_ALREADY_EXISTS_WITH_DIFFERENT_PAYER);
				return;
			}
			*/
		}

		final var result = store.createProvisionally(schedule, fromJava(txnCtx.consensusTime()));
		final var scheduleId = result.getCreated();
		if (null == scheduleId) {
			abortWith(result.getStatus());
			return;
		}

		final var payerKey = txnCtx.activePayerKey();
		final var topLevelKeys = schedule.adminKey().map(ak -> List.of(payerKey, ak)).orElse(List.of(payerKey));
		final var validScheduleKeys = classifier.validScheduleKeys(
				topLevelKeys,
				sigMap,
				activationHelper.currentSigsFn(),
				activationHelper::visitScheduledCryptoSigs);
		final var signingOutcome =
				signingsWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper);
		if (!ACCEPTABLE_SIGNING_OUTCOMES.contains(signingOutcome.getLeft())) {
			abortWith(signingOutcome.getLeft());
			return;
		}

		if (store.isCreationPending()) {
			store.commitCreation();
			final var expiringEntity = new ExpiringEntity(
					EntityId.fromGrpcScheduleId(scheduleId),
					store::expire,
					schedule.expiry());
			txnCtx.addExpiringEntities(Collections.singletonList(expiringEntity));
		}

		var finalOutcome = OK;
		if (signingOutcome.getRight()) {
			finalOutcome = executor.processExecution(scheduleId, store, txnCtx);
		}
		completeContextWith(scheduleId, schedule, finalOutcome == OK ? SUCCESS : finalOutcome);
	}

	private void completeContextWith(ScheduleID scheduleID, MerkleSchedule schedule, ResponseCodeEnum finalOutcome) {
		txnCtx.setCreated(scheduleID);
		txnCtx.setScheduledTxnId(schedule.scheduledTransactionId());
		txnCtx.setStatus(finalOutcome);
	}

	private void abortWith(ResponseCodeEnum cause) {
		if (store.isCreationPending()) {
			store.rollbackCreation();
		}
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasScheduleCreate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(final TransactionBody txnBody) {
		var validity = OK;
		final var op = txnBody.getScheduleCreate();
		if (op.hasAdminKey()) {
			validity = PureValidation.checkKey(op.getAdminKey(), INVALID_ADMIN_KEY);
		}
		if (validity != OK) {
			return validity;
		}

		validity = validator.memoCheck(op.getMemo());
		if (validity != OK) {
			return validity;
		}
		validity = validator.memoCheck(op.getScheduledTransactionBody().getMemo());
		if (validity != OK) {
			return validity;
		}

		return OK;
	}
}
