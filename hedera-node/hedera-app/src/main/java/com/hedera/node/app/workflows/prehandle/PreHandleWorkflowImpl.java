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

package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.meta.InvalidTransactionMetadata;
import com.hedera.node.app.meta.ValidTransactionMetadata;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Implementation of {@link PreHandleWorkflow} */
public class PreHandleWorkflowImpl implements PreHandleWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(PreHandleWorkflowImpl.class);

    /**
     * Per-thread shared resources are shared in a {@link SessionContext}. We store these in a thread local, because we
     * do not have control over the thread pool used by the underlying gRPC server.
     */
    private static final ThreadLocal<SessionContext> SESSION_CONTEXT_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new SessionContext(
                    Query.parser(), Transaction.parser(), SignedTransaction.parser(), TransactionBody.parser()));

    private final WorkflowOnset onset;
    private final TransactionDispatcher dispatcher;
    private final SignaturePreparer signaturePreparer;
    private final Cryptography cryptography;
    private final Function<Runnable, CompletableFuture<Void>> runner;

    /**
     * Constructor of {@code PreHandleWorkflowImpl}
     *
     * @param exe the {@link ExecutorService} to use when submitting new tasks
     * @param dispatcher the {@link TransactionDispatcher} that will call transaction-specific
     * {@code preHandle()}-methods
     * @param onset the {@link WorkflowOnset} that pre-processes the {@link byte[]} of a transaction
     * @param signaturePreparer the {@link SignaturePreparer} to prepare signatures
     * @param cryptography the {@link Cryptography} component used to verify signatures
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    public PreHandleWorkflowImpl(
            @NonNull final ExecutorService exe,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final WorkflowOnset onset,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography) {
        requireNonNull(exe);
        this.dispatcher = requireNonNull(dispatcher);
        this.onset = requireNonNull(onset);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.runner = runnable -> CompletableFuture.runAsync(runnable, exe);
    }

    // Used only for testing
    PreHandleWorkflowImpl(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final WorkflowOnset onset,
            @NonNull final SignaturePreparer signaturePreparer,
            @NonNull final Cryptography cryptography,
            @NonNull final Function<Runnable, CompletableFuture<Void>> runner) {
        this.dispatcher = requireNonNull(dispatcher);
        this.onset = requireNonNull(onset);
        this.signaturePreparer = requireNonNull(signaturePreparer);
        this.cryptography = requireNonNull(cryptography);
        this.runner = requireNonNull(runner);
    }

    @Override
    public void start(@NonNull final HederaState state, @NonNull final Event event) {
        requireNonNull(state);
        requireNonNull(event);

        // Each transaction in the event will go through pre-handle using a background thread
        // from the executor service. The Future representing that work is stored on the
        // platform transaction. The HandleTransactionWorkflow will pull this future back
        // out and use it to block until the pre handle work is done, if needed.
        final ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
        final var itr = event.transactionIterator();
        while (itr.hasNext()) {
            final var platformTx = itr.next();
            final var future = runner.apply(() -> {
                final var metadata = securePreHandle(state, platformTx);
                platformTx.setMetadata(metadata);
            });
            futures.add(future);
        }

        // wait until all transactions were processed before returning
        final CompletableFuture<?>[] array = futures.toArray(new CompletableFuture<?>[0]);
        CompletableFuture.allOf(array).join();
    }

    @Override
    public TransactionMetadata securePreHandle(
            final HederaState state, final com.swirlds.common.system.transaction.Transaction platformTx) {
        try {
            return preHandle(state, platformTx);
        } catch (Exception ex) {
            // Some unknown and unexpected failure happened. If this was non-deterministic, I could
            // end up with an ISS. It is critical that I log whatever happened, because we should
            // have caught all legitimate failures in another catch block.
            LOG.error("An unexpected exception was thrown during pre-handle", ex);
            return createInvalidTransactionMetadata(ResponseCodeEnum.UNKNOWN);
        }
    }

    private TransactionMetadata preHandle(
            final HederaState state, final com.swirlds.common.system.transaction.Transaction platformTx) {
        final OnsetResult onsetResult;
        try {
            // Parse the Transaction and check the syntax
            final var ctx = SESSION_CONTEXT_THREAD_LOCAL.get();
            onsetResult = onset.parseAndCheck(ctx, platformTx.getContents());
        } catch (PreCheckException preCheckException) {
            return createInvalidTransactionMetadata(preCheckException.responseCode());
        }
        final var signatureMap = onsetResult.signatureMap();

        // Call PreTransactionHandler to do transaction-specific checks and get list of required keys
        final var storeFactory = new ReadableStoreFactory(state);
        final var accountStore = storeFactory.createAccountStore();
        final var context = new PreHandleContext(accountStore, onsetResult.txBody(), onsetResult.errorCode());
        dispatcher.dispatchPreHandle(storeFactory, context);

        // Prepare and verify signature-data
        final var txBodyBytes = onsetResult.bodyBytes();
        final var payerSignature = verifyPayerSignature(state, context, txBodyBytes, signatureMap);
        final var otherSignatures = verifyOtherSignatures(state, context, txBodyBytes, signatureMap);

        // Eventually prepare and verify signatures of inner transaction
        final var innerContext = context.getInnerContext();
        TransactionMetadata innerMetadata = null;
        if (innerContext != null) {
            final var innerTxBodyBytes = innerContext.getTxn().toByteArray();
            final var innerPayerSignature = verifyPayerSignature(state, innerContext, innerTxBodyBytes, signatureMap);
            final var innerOtherSignatures = verifyOtherSignatures(state, innerContext, innerTxBodyBytes, signatureMap);
            innerMetadata = createTransactionMetadata(
                    innerContext, signatureMap, innerPayerSignature, innerOtherSignatures, null);
        }

        // 5. Return TransactionMetadata
        return createTransactionMetadata(context, signatureMap, payerSignature, otherSignatures, innerMetadata);
    }

    @Nullable
    private TransactionSignature verifyPayerSignature(
            @NonNull final HederaState state,
            @NonNull final PreHandleContext context,
            @NonNull byte[] txBytes,
            @NonNull SignatureMap signatureMap) {
        if (context.getPayerKey() == null) {
            return null;
        }
        final var payerSignature = signaturePreparer.prepareSignature(state, txBytes, signatureMap, context.getPayer());
        cryptography.verifyAsync(payerSignature);
        return payerSignature;
    }

    @NonNull
    private Map<HederaKey, TransactionSignature> verifyOtherSignatures(
            @NonNull final HederaState state,
            @NonNull final PreHandleContext context,
            @NonNull final byte[] txBodyBytes,
            @NonNull final SignatureMap signatureMap) {
        final var otherSignatures = signaturePreparer.prepareSignatures(
                state, txBodyBytes, signatureMap, context.getRequiredNonPayerKeys());
        cryptography.verifyAsync(new ArrayList<>(otherSignatures.values()));
        return otherSignatures;
    }

    @NonNull
    private Map<HederaKey, TransactionSignature> verifySignatures(
            @NonNull final HederaState state,
            @NonNull final PreHandleContext context,
            @NonNull final byte[] txBodyBytes,
            @NonNull final SignatureMap signatureMap) {
        final Map<HederaKey, >
        if (context.getPayerKey() != null) {
            final var payerSignature = signaturePreparer.prepareSignature(state, txBodyBytes, signatureMap, context.getPayer());
            cryptography.verifyAsync(payerSignature);
        }
        final var otherSignatures = signaturePreparer.prepareSignatures(
                state, txBodyBytes, signatureMap, context.getRequiredNonPayerKeys());
        cryptography.verifyAsync(new ArrayList<>(otherSignatures.values()));
        return otherSignatures;
    }

    @NonNull
    private static TransactionMetadata createTransactionMetadata(
            @NonNull final PreHandleContext context,
            @NonNull final SignatureMap signatureMap,
            @NonNull final Map<HederaKey, TransactionSignature> otherSignatures,
            @Nullable final TransactionMetadata innerMetadata) {
        return new ValidTransactionMetadata(context, signatureMap, otherSignatures, innerMetadata);
    }

    @NonNull
    private static TransactionMetadata createInvalidTransactionMetadata(@NonNull final ResponseCodeEnum responseCode) {
        return new InvalidTransactionMetadata(responseCode);
    }
}
