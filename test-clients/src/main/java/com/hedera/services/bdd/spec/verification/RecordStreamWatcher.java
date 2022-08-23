package com.hedera.services.bdd.spec.verification;

import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

//TODO:
// * make a parent class that watches a givne directory and performs some action on event
// * extend the class for record files/signature files/sidecar files

//TODO:
// * add spec for RecordFile using this
public class RecordStreamWatcher {
  private static final Logger log = LogManager.getLogger(RecordStreamWatcher.class);

  private static final Transaction SKIP_TRANSACTION = Transaction.newBuilder().getDefaultInstanceForType();

  private static final Pattern RECORD_FILE_REGEX =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z.rcd");

  private final Path recordStreamFolderPath;
  private WatchService watchService;

  private boolean hasSeenFirst = false;

  public RecordStreamWatcher(Path recordStreamFolderPath) {
    this.recordStreamFolderPath = recordStreamFolderPath;
  }

  public void prepareInfrastructure() throws IOException {
    watchService = FileSystems.getDefault().newWatchService();
    recordStreamFolderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
  }

  public void watch() throws IOException {
    for (; ; ) {
      // wait for key to be signaled
      WatchKey key;
      try {
        key = watchService.take();
      } catch (InterruptedException x) {
        Thread.currentThread().interrupt();
        return;
      }
      for (final var event : key.pollEvents()) {
        final var kind = event.kind();
        // This key is registered only
        // for ENTRY_CREATE events,
        // but an OVERFLOW event can
        // occur regardless if events
        // are lost or discarded.
        if (kind == StandardWatchEventKinds.OVERFLOW) {
          continue;
        }
        // The filename is the
        // context of the event.
        final var ev = (WatchEvent<Path>) event;
        final var filename = ev.context();
        final var child = recordStreamFolderPath.resolve(filename);
        log.debug("Record stream file created -> {} ", child.getFileName());
        final var newFilePath = child.toAbsolutePath().toString();
        if (RECORD_FILE_REGEX.matcher(newFilePath).find()) {
          final var pair = RecordStreamingUtils.readRecordStreamFile(String.valueOf(child.toAbsolutePath()));
          if (pair.getLeft() == -1) {
            throw new RuntimeException();
          }
          this.onNewRecordStreamFile(pair.getRight().orElseThrow());
        }
      }

      // Reset the key -- this step is critical if you want to
      // receive further watch events.  If the key is no longer valid,
      // the directory is inaccessible so exit the loop.
      boolean valid = key.reset();
      if (!valid) {
        break;
      }
    }
  }

  public void tearDown() {
    try {
      watchService.close();
    } catch (IOException e) {
      log.warn("Watch service couldn't be closed.");
    }
  }

  private List<Transaction> receivedTxns = new ArrayList<>();
  private List<Transaction> sentTxns = new ArrayList<>();
  private int transactionsToSkip = 0;
  private boolean grpcError = false;
  private ExecutorService executorService;

//  public synchronized static RecordStreamWatcher getInstance() {
//    if (instance == null) {
//      instance = new RecordStreamWatcher();
//    }
//    return instance;
//  }


//  public void weAreOn() {
//		executorService = Executors.newSingleThreadExecutor();
//		executorService.submit(
//				() -> {
//					try {
//						new RecordFileDirectoryWatcher();
//					} catch (IOException e) {
//						grpcError = true;
//					}
//				}
//		);
//  }

  private final Queue<Transaction> expectedTransactions = new LinkedBlockingDeque<>();

  private HashObject previousEndRunningHash;
  private Long previousBlockNumber;
  private Timestamp previousBlockLastTxnTimestamp;

  private boolean areTxnsInOrder = true;
  private boolean areRecordsTheSame = true;
  private boolean areBlockNumbersCorrect = true;
  private boolean areHashesCorrect = true;
  private boolean areBlocksSpacedOutInLogPeriod = true;

  private int topLevelTxnsReceived = 0;
  private int toplevelTxnsSent = 0;

  public void addTransaction(final Transaction transaction)  {
    if (transactionsToSkip > 0) {
      // Used when we are using inParallel() transactions and we cannot
      // be sure of the order of the transactions; we just put dummy transactions that we skip
      expectedTransactions.add(SKIP_TRANSACTION);
      transactionsToSkip--;
      return;
    }
    expectedTransactions.add(transaction);

    // USED FOR DEBUGGING
    sentTxns.add(transaction);
    toplevelTxnsSent++;
  }

  public void skipOne() {
    this.transactionsToSkip++;
  }

  public void onNewRecordStreamFile(final RecordStreamFile recordStreamFile) {
    assertHashes(recordStreamFile);
    assertLogPeriodDifferenceBetweenRecordFiles(recordStreamFile);
    assertTransactions(recordStreamFile);
    assertBlockNumbers(recordStreamFile);
  }

  /**
   * Assert startObjectRunningHash[i] == endObjectRunningHash[i-1]
   * @param recordStreamFile
   */
  private void assertHashes(final RecordStreamFile recordStreamFile) {
    final var currentStartRunningHash = recordStreamFile.getStartObjectRunningHash();
    if (previousEndRunningHash != null && !currentStartRunningHash.equals(previousEndRunningHash)) {
      areHashesCorrect = false;
    }
    previousEndRunningHash = recordStreamFile.getEndObjectRunningHash();
  }

  /**
   * Assert first transaction's timestamp in the new file is > lastConensusTimestampFromLastFile + logPeriod
   * @param recordStreamFile
   */
  private void assertLogPeriodDifferenceBetweenRecordFiles(RecordStreamFile recordStreamFile) {
    final var actualTransactions = recordStreamFile
        .getRecordStreamItemsList()
        .stream()
        .toList();
    if (previousBlockLastTxnTimestamp != null) {
      final var currentBlockFirstTimestamp = actualTransactions.get(0).getRecord().getConsensusTimestamp();
      if (
          getPeriod(
              Instant.ofEpochSecond(
                  previousBlockLastTxnTimestamp.getSeconds(),
                  previousBlockLastTxnTimestamp.getNanos())
              , 2000) ==
              getPeriod(
                  Instant.ofEpochSecond(
                      currentBlockFirstTimestamp.getSeconds(),
                      currentBlockFirstTimestamp.getNanos())
                  , 2000)
      ) {
        // New record files should be started only after there is difference > logPeriod between 2 txns
        areBlocksSpacedOutInLogPeriod= false;
      }
    }
    previousBlockLastTxnTimestamp =
        actualTransactions.get(actualTransactions.size() - 1).getRecord().getConsensusTimestamp();
  }

  private void assertTransactions(final RecordStreamFile recordStreamFile) {
    final var actualTransactions = recordStreamFile
        .getRecordStreamItemsList()
        .stream()
        .toList();
    // assert actual transactions in record file are in expected order
    for (int i = 0; i < actualTransactions.size(); i++) {
      final var actualTransactionPair = actualTransactions.get(i);
      if (isChildTransaction(actualTransactions, i, actualTransactionPair))
        continue;

      // USED FOR LOGGING
      receivedTxns.add(actualTransactionPair.getTransaction());
      topLevelTxnsReceived++;

      final var expectedTransaction = expectedTransactions.poll();
      if (SKIP_TRANSACTION.equals(expectedTransaction)) {
        continue;
      }
      if (!Objects.equals(expectedTransaction, actualTransactionPair.getTransaction())) {
        this.areTxnsInOrder = false;
      }
      final var recordFromQuery = getRecordFor(expectedTransaction);
      if (!actualTransactionPair.getRecord().equals(recordFromQuery)) {
        // if record saved in state is not the same as the one exported in record files, fail
        this.areRecordsTheSame = false;
      }
    }
  }

  private boolean isChildTransaction(
      final List<RecordStreamItem> actualTransactions,
      final int positionInFile,
      final RecordStreamItem currentTransactionPair
  ) {
    if (!currentTransactionPair.getRecord().getParentConsensusTimestamp().equals(Timestamp.getDefaultInstance())) {
      // check if succeeding transaction
      return true;
    } else if (positionInFile < actualTransactions.size() - 1) {
      // check if preceding transaction
      final TransactionRecord nextTxnRecord = actualTransactions.get(positionInFile + 1).getRecord();
      if (nextTxnRecord.getParentConsensusTimestamp().equals(Timestamp.getDefaultInstance())) {
        final var nextTxnTimestamp = nextTxnRecord.getConsensusTimestamp();
        final var currentTxnTimestamp = currentTransactionPair.getRecord().getConsensusTimestamp();
        return nextTxnTimestamp.getSeconds() == currentTxnTimestamp.getSeconds() &&
            currentTxnTimestamp.getNanos() + 1 == nextTxnTimestamp.getNanos();
      }
    }
    return false;
  }

  /**
   *
   * We obtain the transaction records when we receive the transaction/transaction record pair in the record stream
   * file.
   *
   * Transaction records are created from the hedera node, so in any case we do it, we have to make a getTxnRecord
   * query at some point after the transaction is executed and saved into state. Normally, with most HapiTxnOp we
   * wait for the transaction to be executed before we continue with the next HapiTxnOp (contract create ->
   * contract call), using the resolveStatus() method in HapiTxnOp. However, some specs use the
   * deferStatusResolution flag, which does not wait for the particular HapiTxnOp to be executed and in state in
   * order to continue with the rest of the HapiTxnOps.
   *
   *
   * @param txnSubmitted
   * @return
   */
  private TransactionRecord getRecordFor(final Transaction txnSubmitted)  {
    final var syntheticSpec = defaultHapiSpec("synthetic").given().when().then();
    HapiGetTxnRecord subOp;
    try {
      subOp = getTxnRecord(extractTxnId(txnSubmitted))
          .noLogging()
          .assertingNothing()
          .suppressStats(true);
//					.nodePayment(syntheticSpec.setup().defaultNodePaymentTinyBars());
    } catch (Throwable e) {
      return null;
    }
    Optional<Throwable> error = subOp.execFor(syntheticSpec);
    if (error.isPresent()) {
      return null;
    }
    return subOp.getResponse().getTransactionGetRecord().getTransactionRecord();
  }

  /**
   * Assert blocknumber[i] = blocknumber[i-1] + 1,
   * @param recordStreamFile
   */
  private void assertBlockNumbers(RecordStreamFile recordStreamFile) {
    final var currentBlockNumber = recordStreamFile.getBlockNumber();
    if (previousBlockNumber != null && previousBlockNumber + 1 != currentBlockNumber) {
      areBlockNumbersCorrect = false;
    }
    previousBlockNumber = currentBlockNumber;
  }


  /* --- Used for feedback of record stream status ---*/

  public static final String recordStreamStatus = """
			\nRunning hashes are correct - %s\s
			Block numbers are correct (increasing consecutively +1) - %s\s
			Record files have logPeriod difference between one another - %s\s
			All transactions are present and in correct order - %s\s
			All records exported are the same as those in state - %s\s
			Top level transactions sent - %d\s
			Top level transactions received - %d""";

  public synchronized boolean getIntermediateValidity() {
    return !grpcError && areHashesCorrect && areBlockNumbersCorrect && areTxnsInOrder && areBlocksSpacedOutInLogPeriod && areRecordsTheSame;
  }

  public synchronized boolean getFinalizedValidity() {
    return !grpcError && areHashesCorrect && areBlockNumbersCorrect && finalizeTxnValidity() && areBlocksSpacedOutInLogPeriod
        && areRecordsTheSame;
  }

  private boolean finalizeTxnValidity() {
    if (!areTxnsInOrder) {
      return false;
    }
    areTxnsInOrder = expectedTransactions.size() == 0;
    return areTxnsInOrder;
  }

  public String getSummary() {
    if (grpcError) {
      return "An error occurred with gRPC channeling. Record stream could not be verified.";
    }
    return String.format(recordStreamStatus, areHashesCorrect, areBlockNumbersCorrect,
        areBlocksSpacedOutInLogPeriod, areTxnsInOrder, areRecordsTheSame,  toplevelTxnsSent,
        topLevelTxnsReceived, sentTxns.toString(), receivedTxns.toString());
  }

  public void shutDown() {
    this.executorService.shutdown();
  }
}
