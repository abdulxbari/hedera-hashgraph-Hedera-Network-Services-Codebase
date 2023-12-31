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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleSign}.
 */
public class ScheduleSignHandler extends AbstractScheduleHandler implements TransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleSign}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @param scheduleStore the {@link ReadableScheduleStore} to use for schedule resolution
     * @param dispatcher the {@link PreHandleDispatcher} that can be used to pre-handle the inner
     *     txn
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(
            @NonNull final PrehandleHandlerContext context,
            @NonNull final ReadableScheduleStore scheduleStore,
            @NonNull final PreHandleDispatcher dispatcher) {
        requireNonNull(context);
        requireNonNull(scheduleStore);
        requireNonNull(dispatcher);
        final var txn = context.getTxn();
        final var op = txn.getScheduleSign();
        final var id = op.getScheduleID();

        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            context.status(INVALID_SCHEDULE_ID);
            return;
        }

        final var scheduledTxn = scheduleLookupResult.get().scheduledTxn();
        final var optionalPayer = scheduleLookupResult.get().designatedPayer();
        final var payerForNested =
                optionalPayer.orElse(scheduledTxn.getTransactionID().getAccountID());

        final var innerMeta = preHandleScheduledTxn(scheduledTxn, payerForNested, dispatcher);
        context.handlerMetadata(innerMeta);
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
}
