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
package com.hedera.services.mocks;

import com.hedera.services.fees.charging.StorageFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;

public class NoopStorageFeeCharging implements StorageFeeCharging {

    @Override
    public void chargeStorageRent(
            long numTotalKvPairs,
            Map<Long, KvUsageInfo> newUsageInfos,
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        // Intentional no-op
    }
}
