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

import com.hederahashgraph.api.proto.java.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionLogger {
    private static final Logger log = LogManager.getLogger(TransactionLogger.class);

    private static final Transaction SKIP_TRANSACTION =
            Transaction.newBuilder().getDefaultInstanceForType();

    private int transactionsToSkip = 0;

    // All the transactions submitted by the specs
    private final List<Transaction> sentTxns = new ArrayList<>();

    public void addTransaction(final Transaction transaction) {
        if (transactionsToSkip > 0) {
            // Used when we are using inParallel() transactions, and we cannot
            // be sure of the order of the transactions; we just put dummy transactions that we skip
            sentTxns.add(SKIP_TRANSACTION);
            transactionsToSkip--;
            return;
        }
        sentTxns.add(transaction);
    }

    public List<Transaction> getSentTxns() {
        return sentTxns;
    }

    public void skipOne() {
        this.transactionsToSkip++;
    }
}
