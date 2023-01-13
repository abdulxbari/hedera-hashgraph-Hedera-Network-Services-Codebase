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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAssociateToAccount}.
 */
public class TokenAssociateToAccountHandler implements TransactionHandler {

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenAssociateToAccount} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param keyLookup the {@link AccountKeyLookup} to use to resolve keys
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txn,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup keyLookup) {

        final var op = Objects.requireNonNull(txn).getTokenAssociate();
        final var meta =
                new SigTransactionMetadataBuilder(keyLookup).payerKeyFor(payer).txnBody(txn);
        final var target = op.getAccount();

        return meta.addNonPayerKey(target, INVALID_ACCOUNT_ID).build();
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
        throw new UnsupportedOperationException("Not implemented");
    }
}
