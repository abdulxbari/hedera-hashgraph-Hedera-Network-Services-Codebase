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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.state.migration.QueryableRecords.NO_QUERYABLE_RECORDS;
import static com.hedera.node.app.service.mono.utils.MiscUtils.forEach;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Encapsulates storage of <i>payer records</i>, which summarize the results of a transaction and
 * are kept in state for 180 consensus seconds. They are called "payer" records because of the
 * {@code getAccountRecords} HAPI query, whose contract is to return the latest records in in state
 * whose fees were paid by a given {@link com.hederahashgraph.api.proto.java.AccountID}.
 *
 * <p>Without the {@code getAccountRecords} query, we could store all records in a single huge
 * {@link FCQueue} in state. But with the query, that would entail an auxiliary data structure; so
 * we use an in-state representation that explicitly maps from payer id to an {@link FCQueue} with
 * that payer's records.
 *
 * <ul>
 *   <li>When accounts are in memory, each account is an internal node of a {@link MerkleMap}; we
 *       can just use a {@link FCQueue} child for the records of each such internal node.
 *   <li>When accounts are on disk, each account's {@link FCQueue} is wrapped in a {@link
 *       MerklePayerRecords} leaf of a <b>record-specific</b> {@link MerkleMap}.
 * </ul>
 */
public class RecordsStorageAdapter {
    private static final Logger logger = LogManager.getLogger(RecordsStorageAdapter.class);
    private final boolean accountsOnDisk;
    private final @Nullable MerkleMapLike<EntityNum, MerkleAccount> legacyAccounts;
    private final @Nullable FCQueue<ExpirableTxnRecord> allRecords;
    private static final ConcurrentHashMap<EntityNum, Deque<ExpirableTxnRecord>> payerRecords = new ConcurrentHashMap<>();

    public static RecordsStorageAdapter fromLegacy(final MerkleMapLike<EntityNum, MerkleAccount> accounts) {
        return new RecordsStorageAdapter(accounts, null);
    }

    public static RecordsStorageAdapter fromDedicated(final FCQueue<ExpirableTxnRecord> allRecords) {
        return new RecordsStorageAdapter(null, allRecords);
    }

    private RecordsStorageAdapter(
            @Nullable final MerkleMapLike<EntityNum, MerkleAccount> accounts,
            @Nullable final FCQueue<ExpirableTxnRecord> allRecords) {
        this.accountsOnDisk = accounts == null;
        this.legacyAccounts = accounts;
        this.allRecords = allRecords;
    }

    /**
     * Performs any work needed to track records for a given payer account.
     *
     * @param payerNum the new payer number
     */
    public void prepForPayer(final EntityNum payerNum) {
        // If accounts are in memory, the needed FCQ was created as a
        // side-effect of creating the account itself
        if (accountsOnDisk) {
            payerRecords.put(payerNum, new ArrayDeque<>());
//            logger.info("prepForPayer {} num: {}", this, payerNum.intValue());
        }
    }

    public void forgetPayer(final EntityNum payerNum) {
        // If accounts are in memory, the needed FCQ was removed as a
        // side-effect of removing the account itself
        if (accountsOnDisk) {
            payerRecords.remove(payerNum);
//            logger.info("forgetPayer {}", payerNum.intValue());
        }
    }

    public void addPayerRecord(final AccountID payer, final ExpirableTxnRecord payerRecord) {
        addPayerRecord(EntityNum.fromAccountId(payer), payerRecord);
    }

    public void addPayerRecord(final EntityNum payerNum, final ExpirableTxnRecord payerRecord) {
        if (accountsOnDisk) {
//            logger.info("addPayerRecord {} num: {} rec: {}", this, payerNum.intValue(), payerRecord.getExpiry());
            final var mutableRecords = payerRecords.computeIfAbsent(payerNum, key -> new ArrayDeque<>());
            mutableRecords.offer(payerRecord);
            payerRecord.setPayer(payerNum);
            allRecords.add(payerRecord);
        } else {
            final var mutableAccount = legacyAccounts.getForModify(payerNum);
            mutableAccount.records().offer(payerRecord);
        }
    }

    public Queue<ExpirableTxnRecord> getMutablePayerRecords(final EntityNum payerNum) {
        if (accountsOnDisk) {
            final var mutableRecords = payerRecords.get(payerNum);
            return mutableRecords;
        } else {
            final var mutableAccount = legacyAccounts.getForModify(payerNum);
            return mutableAccount.records();
        }
    }

    public void purgeExpiredRecordsAt(long now, Consumer<ExpirableTxnRecord> consumer) {
        for (int count = 0; ; ++count) {
            ExpirableTxnRecord nextRecord = allRecords.peek();
            if (nextRecord == null || nextRecord.getExpiry() > now) {
//                logger.info("purgeExpiredRecordsAt {} count {} remaining {}", now, count, allRecords.size());
                return;
            }
            nextRecord = allRecords.poll();
            EntityNum payerNum = nextRecord.getPayer();
            var mutableRecords = payerRecords.get(payerNum);
            if (!nextRecord.equals(mutableRecords.poll())) {
                logger.error("Inconsistent RecordsStorageAdapter state acc: {}", payerNum);
            }
            consumer.accept(nextRecord);
        }
    }

    public QueryableRecords getReadOnlyPayerRecords(final EntityNum payerNum) {
        if (accountsOnDisk) {
            final var payerRecordsView = payerRecords.get(payerNum);
            return (payerRecordsView == null) ? NO_QUERYABLE_RECORDS : new QueryableRecords(payerRecordsView.size(), payerRecordsView.iterator());
        } else {
            final var payerAccountView = legacyAccounts.get(payerNum);
            return (payerAccountView == null)
                    ? NO_QUERYABLE_RECORDS
                    : new QueryableRecords(payerAccountView.numRecords(), payerAccountView.recordIterator());
        }
    }

    public void doForEach(final BiConsumer<EntityNum, Queue<ExpirableTxnRecord>> observer) {
        if (accountsOnDisk) {
            payerRecords.forEach(
                    (payerNum, accountRecords) -> observer.accept(payerNum, accountRecords));
        } else {
            forEach(legacyAccounts, (payerNum, account) -> observer.accept(payerNum, account.records()));
        }
    }
}
