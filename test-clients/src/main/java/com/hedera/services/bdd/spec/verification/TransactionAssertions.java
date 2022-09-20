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
package com.hedera.services.bdd.spec.verification;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;

import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionAssertions {

    private static final Logger log = LogManager.getLogger(TransactionAssertions.class);

    private static final Transaction SKIP_TRANSACTION =
            Transaction.newBuilder().getDefaultInstanceForType();

    private boolean hasSeenFirstExpectedTxn = false;
    private boolean areTxnsInOrder = true;
    private boolean areRecordsTheSame = true;

    private int transactionsToSkip = 0;

    private final List<Transaction> expectedTxns;
    private int current = 0;

    public TransactionAssertions(List<Transaction> expectedTxns) {
        this.expectedTxns = expectedTxns;
    }

    public void onNewRecordStreamFile(final List<RecordStreamItem> actualTransactions) {
        if (current >= expectedTxns.size()) {
            return;
        }
        // assert actual transactions in record file are in expected order
        for (int i = 0; i < actualTransactions.size(); i++) {
            final var actualTransactionPair = actualTransactions.get(i);
            if (isChildTransaction(actualTransactions, i, actualTransactionPair)) {
                // child transactions are created in the consensus nodes, not sent by the clients
                // we cannot *easily* reconstruct the child transaction protobuf, so we skip them
                // for now
                continue;
            }
            final var expectedTransaction = expectedTxns.get(current);
            if (!hasSeenFirstExpectedTxn) {
                if (!Objects.equals(expectedTransaction, actualTransactionPair.getTransaction())) {
                    continue;
                } else {
                    hasSeenFirstExpectedTxn = true;
                }
            }
            current++;
            if (SKIP_TRANSACTION.equals(expectedTransaction)) {
                continue;
            }
            if (!Objects.equals(expectedTransaction, actualTransactionPair.getTransaction())) {
                // once we see the first sent txn, we expect the next to be in order
                log.warn("Mismatch between order of sent txns and order of txns in record files.");
                this.areTxnsInOrder = false;
            }
            final var recordInState = getTxnRecordFor(expectedTransaction);
            final var recordInFile = actualTransactionPair.getRecord();
            if (!recordInFile.equals(recordInState)) {
                // if record saved in state is not the same as the one exported in record files,
                // fail
                log.warn(
                        "Mismatch between transaction record in state {} and transaction record in"
                                + " record file {}",
                        recordInState,
                        recordInFile);
                this.areRecordsTheSame = false;
            }
        }
    }

    private boolean isChildTransaction(
            final List<RecordStreamItem> actualTransactions,
            final int positionInFile,
            final RecordStreamItem currentTransactionPair) {
        if (!currentTransactionPair
                .getRecord()
                .getParentConsensusTimestamp()
                .equals(Timestamp.getDefaultInstance())) {
            // check if succeeding transaction
            return true;
        } else if (positionInFile < actualTransactions.size() - 1) {
            // check if preceding transaction
            final TransactionRecord nextTxnRecord =
                    actualTransactions.get(positionInFile + 1).getRecord();
            if (nextTxnRecord
                    .getParentConsensusTimestamp()
                    .equals(Timestamp.getDefaultInstance())) {
                final var nextTxnTimestamp = nextTxnRecord.getConsensusTimestamp();
                final var currentTxnTimestamp =
                        currentTransactionPair.getRecord().getConsensusTimestamp();
                return nextTxnTimestamp.getSeconds() == currentTxnTimestamp.getSeconds()
                        && currentTxnTimestamp.getNanos() + 1 == nextTxnTimestamp.getNanos();
            }
        }
        return false;
    }

    /**
     * We obtain the transaction records when we receive the transaction/transaction record pair in
     * the record stream file.
     *
     * <p>Transaction records are created from the hedera node, so in any case we do it, we have to
     * make a getTxnRecord query at some point after the transaction is executed and saved into
     * state. Normally, with most HapiTxnOp we wait for the transaction to be executed before we
     * continue with the next HapiTxnOp (contract create -> contract call), using the
     * resolveStatus() method in HapiTxnOp. However, some specs use the deferStatusResolution flag,
     * which does not wait for the particular HapiTxnOp to be executed and in state in order to
     * continue with the rest of the HapiTxnOps.
     *
     * @param txnSubmitted
     * @return
     */
    private TransactionRecord getTxnRecordFor(final Transaction txnSubmitted) {
        // FUTURE WORK: save txn record obtained in spec, instead of issuing the same query here
        final var syntheticSpec = defaultHapiSpec("synthetic").given().when().then();
        syntheticSpec.tryReinitializingFees();
        HapiGetTxnRecord txnRecordOp;
        try {
            txnRecordOp =
                    getTxnRecord(extractTxnId(txnSubmitted))
                            .noLogging()
                            .assertingNothing()
                            .suppressStats(true);
        } catch (Throwable e) {
            return null;
        }
        Optional<Throwable> error = txnRecordOp.execFor(syntheticSpec);
        if (error.isPresent()) {
            return null;
        }
        return txnRecordOp.getResponse().getTransactionGetRecord().getTransactionRecord();
    }

    public boolean getFinalizedValidity() {
        return hasSeenFirstExpectedTxn && areTxnsInOrder && areRecordsTheSame;
    }

    public void resetForNewNode() {
        current = 0;
    }
}
