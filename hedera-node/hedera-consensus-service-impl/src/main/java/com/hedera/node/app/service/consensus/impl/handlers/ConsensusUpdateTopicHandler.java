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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.exceptions.HandleStatusException.validateFalse;
import static com.hedera.node.app.spi.exceptions.HandleStatusException.validateTrue;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.UpdateTopicRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_UPDATE_TOPIC}.
 */
@Singleton
public class ConsensusUpdateTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusUpdateTopicHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *                passed to {@code #handle()}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore) {
        requireNonNull(context);
        final var op = context.getTxn().consensusUpdateTopicOrThrow();

        if (onlyExtendsExpiry(op)) {
            return;
        }

        final var topicMeta = topicStore.getTopicMetadata(op.topicIDOrElse(TopicID.DEFAULT));
        if (topicMeta.failed()) {
            context.status(INVALID_TOPIC_ID);
            return;
        }

        final var adminKey = topicMeta.metadata().adminKey();
        if (adminKey.isPresent()) {
            context.addToReqNonPayerKeys(adminKey.get());
        }

        if (op.hasAdminKey()) {
            asHederaKey(op.adminKeyOrThrow()).ifPresent(context::addToReqNonPayerKeys);
        }
        if (op.hasAutoRenewAccount() && !AccountID.DEFAULT.equals(op.autoRenewAccount())) {
            context.addNonPayerKey(op.autoRenewAccountOrElse(AccountID.DEFAULT), INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private boolean onlyExtendsExpiry(final ConsensusUpdateTopicTransactionBody op) {
        return op.hasExpirationTime()
                && !op.hasMemo()
                && !op.hasAdminKey()
                && !op.hasSubmitKey()
                && !op.hasAutoRenewPeriod()
                && !op.hasAutoRenewAccount();
    }

    /**
     * Given the appropriate context, updates a topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @param topicUpdate   the {@link ConsensusUpdateTopicTransactionBody} of the active transaction
     * @param topicStore    the {@link WritableTopicStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusUpdateTopicTransactionBody topicUpdate,
            @NonNull final WritableTopicStore topicStore) {
        final var maybeTopic = requireNonNull(topicStore)
                .get(topicUpdate.topicIDOrElse(TopicID.DEFAULT).topicNum());
        validateTrue(maybeTopic.isPresent(), INVALID_TOPIC_ID);
        final var topic = maybeTopic.get();
        validateFalse(topic.deleted(), INVALID_TOPIC_ID);

        // First validate this topic is mutable; and the pending mutations are allowed
        validateFalse(topic.adminKey() == null && wantsToMutateNonExpiryField(topicUpdate), UNAUTHORIZED);
        validateMaybeNewAttributes(handleContext, topicUpdate, topic);

        // Now we apply the mutations to a builder
        final var builder = new Topic.Builder();
        // But first copy over the immutable topic attributes to the builder
        builder.topicNumber(topic.topicNumber());
        builder.sequenceNumber(topic.sequenceNumber());
        builder.runningHash(topic.runningHash());
        builder.deleted(topic.deleted());
        // And then resolve mutable attributes, and put the new topic back
        resolveMutableBuilderAttributes(handleContext.expiryValidator(), topicUpdate, builder, topic);
        topicStore.put(builder.build());
    }

    private void resolveMutableBuilderAttributes(
            final ExpiryValidator expiryValidator,
            final ConsensusUpdateTopicTransactionBody op,
            final Topic.Builder builder,
            final Topic topic) {
        if (op.hasAdminKey()) {
            builder.adminKey(op.adminKey());
        } else {
            builder.adminKey(topic.adminKey());
        }
        if (op.hasSubmitKey()) {
            builder.submitKey(op.submitKey());
        } else {
            builder.submitKey(topic.submitKey());
        }
        if (op.hasMemo()) {
            builder.memo(op.memo());
        } else {
            builder.memo(topic.memo());
        }
        final var resolvedExpiryMeta = resolvedUpdateMetaFrom(expiryValidator, op, topic);
        builder.expiry(resolvedExpiryMeta.expiry());
        builder.autoRenewPeriod(resolvedExpiryMeta.autoRenewPeriod());
        builder.autoRenewAccountNumber(resolvedExpiryMeta.autoRenewNum());
    }

    private void validateMaybeNewAttributes(
            final HandleContext handleContext, final ConsensusUpdateTopicTransactionBody op, final Topic topic) {
        validateMaybeNewAdminKey(handleContext.attributeValidator(), op);
        validateMaybeNewSubmitKey(handleContext.attributeValidator(), op);
        validateMaybeNewMemo(handleContext.attributeValidator(), op);
        validateMaybeNewExpiry(handleContext.expiryValidator(), op, topic);
    }

    private void validateMaybeNewExpiry(
            final ExpiryValidator expiryValidator, final ConsensusUpdateTopicTransactionBody op, final Topic topic) {
        resolvedUpdateMetaFrom(expiryValidator, op, topic);
    }

    private ExpiryMeta resolvedUpdateMetaFrom(
            final ExpiryValidator expiryValidator, final ConsensusUpdateTopicTransactionBody op, final Topic topic) {
        final var currentMeta = new ExpiryMeta(topic.expiry(), topic.autoRenewPeriod(), topic.autoRenewAccountNumber());
        if (op.hasExpirationTime() || op.hasAutoRenewPeriod() || op.hasAutoRenewAccount()) {
            final var updateMeta = new ExpiryMeta(
                    op.hasExpirationTime() ? op.expirationTimeOrThrow().seconds() : NA,
                    op.hasAutoRenewPeriod() ? op.autoRenewPeriodOrThrow().seconds() : NA,
                    // Shard and realm will be ignored if num is NA
                    op.autoRenewAccountOrElse(AccountID.DEFAULT).shardNum(),
                    op.autoRenewAccountOrElse(AccountID.DEFAULT).realmNum(),
                    op.hasAutoRenewAccount() ? op.autoRenewAccountOrThrow().accountNumOrElse(NA) : NA);
            return expiryValidator.resolveUpdateAttempt(currentMeta, updateMeta);
        } else {
            return currentMeta;
        }
    }

    private void validateMaybeNewMemo(
            final AttributeValidator attributeValidator, final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasMemo()) {
            attributeValidator.validateMemo(op.memo());
        }
    }

    private void validateMaybeNewAdminKey(
            final AttributeValidator attributeValidator, final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasAdminKey()) {
            attributeValidator.validateKey(op.adminKey());
        }
    }

    private void validateMaybeNewSubmitKey(
            final AttributeValidator attributeValidator, final ConsensusUpdateTopicTransactionBody op) {
        if (op.hasSubmitKey()) {
            attributeValidator.validateKey(op.submitKey());
        }
    }

    @Override
    public ConsensusUpdateTopicRecordBuilder newRecordBuilder() {
        return new UpdateTopicRecordBuilder();
    }

    public static boolean wantsToMutateNonExpiryField(final ConsensusUpdateTopicTransactionBody op) {
        return op.hasMemo()
                || op.hasAdminKey()
                || op.hasSubmitKey()
                || op.hasAutoRenewPeriod()
                || op.hasAutoRenewAccount();
    }
}
