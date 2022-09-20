package com.hedera.services.bdd.spec.verification;

import com.hederahashgraph.api.proto.java.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransactionLogger {
  private static final Logger log = LogManager.getLogger(TransactionLogger.class);

  private static final Transaction SKIP_TRANSACTION = Transaction.newBuilder().getDefaultInstanceForType();

  private int transactionsToSkip = 0;

  // All the transactions submitted by the specs
  private final List<Transaction> sentTxns = new ArrayList<>();

  public void addTransaction(final Transaction transaction)  {
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
