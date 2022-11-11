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
package com.hedera.services.state.migration;

import static com.hedera.services.state.migration.StateChildIndices.*;
import static com.hedera.services.utils.MiscUtils.forEach;
import static com.hedera.services.utils.MiscUtils.withLoggedDuration;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.entities.FungibleOnDiskToken;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.state.virtual.entities.OnDiskTokenRel;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MapMigrationToDisk {
    private static final Logger log = LogManager.getLogger(MapMigrationToDisk.class);

    public static final int INSERTIONS_PER_COPY = 10_000;

    public static void migrateToDiskAsApropos(
            final int insertionsPerCopy,
            final ServicesState mutableState,
            final ToDiskMigrations toDiskMigrations,
            final VirtualMapFactory virtualMapFactory,
            final Function<MerkleAccountState, OnDiskAccount> accountMigrator,
            final Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator,
            final Function<MerkleToken, FungibleOnDiskToken> nonUniqueTokenMigrator) {
        if (toDiskMigrations.doAccounts()) {
            migrateAccountsToDisk(
                    insertionsPerCopy, mutableState, virtualMapFactory, accountMigrator);
        }
        if (toDiskMigrations.doTokenRels()) {
            migrateRelsToDisk(insertionsPerCopy, mutableState, virtualMapFactory, tokenRelMigrator);
        }
        if (toDiskMigrations.doNonUniqueTokens()) {
          migrateNonUniqueTokensToDisk(insertionsPerCopy, mutableState, virtualMapFactory, nonUniqueTokenMigrator);
        }
    }

    @SuppressWarnings("unchecked")
    private static void migrateAccountsToDisk(
            final int insertionsPerCopy,
            final ServicesState mutableState,
            final VirtualMapFactory virtualMapFactory,
            final Function<MerkleAccountState, OnDiskAccount> accountMigrator) {
        final var insertionsSoFar = new AtomicInteger(0);
        final NonAtomicReference<VirtualMap<EntityNumVirtualKey, OnDiskAccount>> onDiskAccounts =
                new NonAtomicReference<>(virtualMapFactory.newOnDiskAccountStorage());

        final var inMemoryAccounts =
                (MerkleMap<EntityNum, MerkleAccount>) mutableState.getChild(ACCOUNTS);
        final MerkleMap<EntityNum, MerklePayerRecords> payerRecords = new MerkleMap<>();
        withLoggedDuration(
                () ->
                        forEach(
                                inMemoryAccounts,
                                (num, account) -> {
                                    final var accountRecords = new MerklePayerRecords();
                                    account.records().forEach(accountRecords::offer);
                                    payerRecords.put(num, accountRecords);

                                    final var onDiskAccount =
                                            accountMigrator.apply(account.state());
                                    onDiskAccounts
                                            .get()
                                            .put(
                                                    new EntityNumVirtualKey(num.longValue()),
                                                    onDiskAccount);
                                    if (insertionsSoFar.incrementAndGet() % insertionsPerCopy
                                            == 0) {
                                        final var onDiskAccountsCopy = onDiskAccounts.get().copy();
                                        onDiskAccounts.set(onDiskAccountsCopy);
                                    }
                                }),
                log,
                "accounts-to-disk migration");
        mutableState.setChild(ACCOUNTS, onDiskAccounts.get());
        mutableState.setChild(PAYER_RECORDS, payerRecords);
    }

    @SuppressWarnings("unchecked")
    private static void migrateRelsToDisk(
            final int insertionsPerCopy,
            final ServicesState mutableState,
            final VirtualMapFactory virtualMapFactory,
            final Function<MerkleTokenRelStatus, OnDiskTokenRel> relMigrator) {
        final var insertionsSoFar = new AtomicInteger(0);
        final NonAtomicReference<VirtualMap<EntityNumVirtualKey, OnDiskTokenRel>> onDiskRels =
                new NonAtomicReference<>(virtualMapFactory.newOnDiskTokenRels());

        final var inMemoryRels =
                (MerkleMap<EntityNumPair, MerkleTokenRelStatus>)
                        mutableState.getChild(TOKEN_ASSOCIATIONS);
        withLoggedDuration(
                () ->
                        forEach(
                                inMemoryRels,
                                (numPair, rel) -> {
                                    final var onDiskRel = relMigrator.apply(rel);
                                    onDiskRels
                                            .get()
                                            .put(EntityNumVirtualKey.fromPair(numPair), onDiskRel);
                                    if (insertionsSoFar.incrementAndGet() % insertionsPerCopy
                                            == 0) {
                                        final var onDiskRelCopy = onDiskRels.get().copy();
                                        onDiskRels.set(onDiskRelCopy);
                                    }
                                }),
                log,
                "token-rels-to-disk migration");
        mutableState.setChild(TOKEN_ASSOCIATIONS, onDiskRels.get());
    }

  private static void migrateNonUniqueTokensToDisk(
      final int insertionsPerCopy,
      final ServicesState mutableState,
      final VirtualMapFactory virtualMapFactory,
      final Function<MerkleToken, FungibleOnDiskToken> nonUniqueTokensMigrator) {

      throw new NotImplementedException();
  }

    private MapMigrationToDisk() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
