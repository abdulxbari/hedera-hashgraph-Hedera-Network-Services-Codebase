/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.AccountStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LazyCreationLogic {
    private final AliasManager aliasManager;
    private final AccountStore accountStore;

    @Inject
    public LazyCreationLogic(
            final AliasManager aliasManager, final AccountStore accountStore) {
        this.aliasManager = aliasManager;
        this.accountStore = accountStore;
    }

    public void tryToComplete(ByteString evmAddress, JKey payerKey) {
        final var aliasedAccountId = aliasManager.lookupIdBy(evmAddress).toId();
        final var account = accountStore.loadAccount(aliasedAccountId);
        final var isLazyCreated = account.getAlias().equals(evmAddress) && account.getKey() == null;
        if (isLazyCreated) {
            account.setKey(payerKey);
            accountStore.commitAccount(account);
        }
    }
}
