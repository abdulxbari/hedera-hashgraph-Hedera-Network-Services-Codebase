/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Provides the state transition for associating tokens to an account. */
@Singleton
public class TokenAssociateTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final AssociateLogic associateLogic;
    private final AliasManager aliasManager;
    private final AccountStore accountStore;

    @Inject
    public TokenAssociateTransitionLogic(
            final TransactionContext txnCtx, final AssociateLogic associateLogic, final AliasManager aliasManager, final AccountStore accountStore) {
        this.txnCtx = txnCtx;
        this.associateLogic = associateLogic;
        this.aliasManager = aliasManager;
        this.accountStore = accountStore;
    }

    @Override
    public void doStateTransition() {
        /* --- Translate from gRPC types --- */
        final var op = txnCtx.accessor().getTxn().getTokenAssociate();
        final var accountId = Id.fromGrpcAccount(op.getAccount());

        /* --- Do the business logic --- */
        final var payerKey = txnCtx.activePayerKey();
        final var ecdsaBytes = payerKey != null ? payerKey.getECDSASecp256k1Key() : new byte[0];
        if (ecdsaBytes.length > 0) {
            final var evmAddress = ByteString.copyFrom(EthTxSigs.recoverAddressFromPubKey(ecdsaBytes));
            final var aliasedAccountId = aliasManager.lookupIdBy(evmAddress).toId();
            final var account = accountStore.loadAccount(aliasedAccountId);
            final var isLazyCreated = account.getAlias().equals(evmAddress) && account.getKey() == null;
            if (isLazyCreated) {
                account.setKey(payerKey);
                accountStore.commitAccount(account);
            }
        }

        associateLogic.associate(accountId, op.getTokensList());
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasTokenAssociate;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        return associateLogic.validateSyntax(txnBody);
    }
}
