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
package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoDeleteAllowanceHandlerTest extends CryptoHandlerTestBase {
    private final TokenID nft = asToken("0.0.56789");
    private final AccountID owner = asAccount("0.0.123456");
    private final HederaKey ownerKey = asHederaKey(A_COMPLEX_KEY).get();
    @Mock private MerkleAccount ownerAccount;

    private CryptoDeleteAllowanceHandler subject = new CryptoDeleteAllowanceHandler();

    @Test
    void cryptoDeleteAllowanceVanilla() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoDeleteAllowanceTransaction(payer);
        final var context = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context);
        basicMetaAssertions(context, 1, false, OK);
        assertEquals(payerKey, context.getPayerKey());
        assertIterableEquals(List.of(ownerKey), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoDeleteAllowanceDoesntAddIfOwnerSameAsPayer() {
        given(accounts.get(owner.getAccountNum())).willReturn(ownerAccount);
        given(ownerAccount.getAccountKey()).willReturn((JKey) ownerKey);

        final var txn = cryptoDeleteAllowanceTransaction(owner);
        final var context = new PrehandleHandlerContext(store, txn, owner);
        subject.preHandle(context);
        basicMetaAssertions(context, 0, false, OK);
        assertEquals(ownerKey, context.getPayerKey());
        assertIterableEquals(List.of(), context.getRequiredNonPayerKeys());
    }

    @Test
    void cryptoDeleteAllowanceFailsIfPayerOrOwnerNotExist() {
        var txn = cryptoDeleteAllowanceTransaction(owner);
        given(accounts.get(owner.getAccountNum())).willReturn(null);

        final var context1 = new PrehandleHandlerContext(store, txn, owner);
        subject.preHandle(context1);
        basicMetaAssertions(context1, 0, true, INVALID_PAYER_ACCOUNT_ID);
        assertNull(context1.getPayerKey());
        assertIterableEquals(List.of(), context1.getRequiredNonPayerKeys());

        txn = cryptoDeleteAllowanceTransaction(payer);
        final var context2 = new PrehandleHandlerContext(store, txn, payer);
        subject.preHandle(context2);
        basicMetaAssertions(context2, 0, true, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, context2.getPayerKey());
        assertIterableEquals(List.of(), context2.getRequiredNonPayerKeys());
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
    }

    private TransactionBody cryptoDeleteAllowanceTransaction(final AccountID id) {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(id)
                        .setTransactionValidStart(consensusTimestamp);
        final var allowanceTxnBody =
                CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addNftAllowances(
                                NftRemoveAllowance.newBuilder()
                                        .setOwner(owner)
                                        .setTokenId(nft)
                                        .addAllSerialNumbers(List.of(1L, 2L, 3L))
                                        .build())
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoDeleteAllowance(allowanceTxnBody)
                .build();
    }
}
