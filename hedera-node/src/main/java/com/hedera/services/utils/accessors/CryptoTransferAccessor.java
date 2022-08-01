/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;

/**
 * Specialized accessor for CryptoTransfer transaction. Uses the latest signed state view for looking up
 * alias in the ServicesState.
 */
public class CryptoTransferAccessor extends SignedTxnAccessor {
    private final CryptoTransferTransactionBody body;
    private final GlobalDynamicProperties properties;

    public CryptoTransferAccessor(
            final byte[] signedTxnWrapperBytes,
            final Transaction txn,
            final GlobalDynamicProperties properties)
            throws InvalidProtocolBufferException {
        super(signedTxnWrapperBytes, txn);
        this.body = getTxn().getCryptoTransfer();
        this.properties = properties;
        setXferUsageMeta();
    }

    @Override
    public boolean supportsPrecheck() {
        return true;
    }

    @Override
    public ResponseCodeEnum doPrecheck() {
        return validateSyntax();
    }

    private void setXferUsageMeta() {
        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        for (var tokenTransfers : body.getTokenTransfersList()) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.getTransfersCount();
            numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
        }
        getSpanMapAccessor().setCryptoTransferMeta(
                this, new CryptoTransferMeta(
                        1, totalTokensInvolved, totalTokenTransfers, numNftOwnershipChanges));
    }

    public ResponseCodeEnum validateSyntax() {
        final var impliedTransfers = getSpanMapAccessor().getImpliedTransfers(this);
        if (impliedTransfers != null) {
            /* Accessor is for a consensus transaction with a expand-handle span
             * we've been managing in the normal way. */
            return impliedTransfers.getMeta().code();
        } else {
            /* Accessor is for either (1) a transaction in precheck; or (2) a scheduled
            transaction that reached consensus without a managed expand-handle span; or
            (3) in a development environment, a transaction submitted via UncheckedSubmit. */
            final var validationProps =
                    new ImpliedTransfersMeta.ValidationProps(
                            properties.maxTransferListSize(),
                            properties.maxTokenTransferListSize(),
                            properties.maxNftTransfersLen(),
                            properties.maxCustomFeeDepth(),
                            properties.maxXferBalanceChanges(),
                            properties.areNftsEnabled(),
                            properties.isAutoCreationEnabled(),
                            properties.areAllowancesEnabled());
            final var op = getTxn().getCryptoTransfer();
            return PureTransferSemanticChecks.fullPureValidation(
                    op.getTransfers(), op.getTokenTransfersList(), validationProps);
        }
    }
}
