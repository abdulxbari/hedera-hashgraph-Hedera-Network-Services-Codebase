package com.hedera.node.app.records;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.*;
import com.hedera.node.app.records.files.RecordStreamV6Test;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Test data for record stream file tests
 */
@SuppressWarnings("DataFlowIssue")
public class RecordTestData {
    private static final Random RANDOM = new Random(123456789L);
    private static Bytes randomBytes() {
        final byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);
        return Bytes.wrap(bytes);
    }
    /** Test Block Num **/
    public static final long BLOCK_NUM = RANDOM.nextInt(0, Integer.MAX_VALUE);
    /** Test Version */
    public static final SemanticVersion VERSION = new SemanticVersion(1, 2, 3, "", "");
    /** A hash used as the start of running hash changes in tests */
    public static final Hash STARTING_RUNNING_HASH;
    public static final HashObject STARTING_RUNNING_HASH_OBJ;

    /** List of test blocks, each containing a number of transaction records */
    public static final List<List<SingleTransactionRecord>> TEST_BLOCKS;

    /** blocks to create, true means generate sidecar items for transactions in that block */
    public static final boolean[] TEST_BLOCKS_WITH_SIDECARS = new boolean[] {false, true, true, true, false, true, false, false, true};

    static {
        try {
            final List<List<SingleTransactionRecord>> testBlocks = new ArrayList<>();
            // load real record stream items from a JSON resource file
            final Path jsonPath = Path.of(RecordStreamV6Test.class.getResource("/record-files/2023-05-01T00_00_24.038693760Z.json").toURI());
            final RecordStreamFile recordStreamFile = RecordStreamFile.JSON.parse(new ReadableStreamingData(Files.newInputStream(jsonPath)));
            final List<SingleTransactionRecord> realRecordStreamItems = recordStreamFile.recordStreamItems()
                    .stream()
                    .map(item -> new SingleTransactionRecord(item.transaction(), item.record(), Collections.emptyList()))
                    .toList();

            // for the first block, use the loaded transactions as is
            testBlocks.add(realRecordStreamItems);
            // now add blocks for TEST_BLOCKS_TYPES types
            Instant firstTransactionConsensusTime = Instant.ofEpochSecond(
                    realRecordStreamItems.get(0).record().consensusTimestamp().seconds(),
                    realRecordStreamItems.get(0).record().consensusTimestamp().nanos());
            for (int j = 1; j < TEST_BLOCKS_WITH_SIDECARS.length; j++) {
                boolean generateSidecarItems = TEST_BLOCKS_WITH_SIDECARS[j];
                final int count = 100 + RANDOM.nextInt(1000);
                firstTransactionConsensusTime = firstTransactionConsensusTime.plusSeconds(2);
                Instant consenusTime = firstTransactionConsensusTime;
                List<SingleTransactionRecord> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    SingleTransactionRecord item = realRecordStreamItems.get(RANDOM.nextInt(realRecordStreamItems.size()));
                    items.add(changeTransactionConsensusTimeAndGenerateSideCarItems(consenusTime, item, generateSidecarItems));
                    consenusTime = consenusTime.plusNanos(10);
                }
                testBlocks.add(items);
            }
            TEST_BLOCKS = Collections.unmodifiableList(testBlocks);
            // create a random hash for running hash start
            MessageDigest messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            messageDigest.update(Double.toString(Math.random()).getBytes());
            final byte[] runningHashStart = messageDigest.digest();
            STARTING_RUNNING_HASH = new Hash(runningHashStart, DigestType.SHA_384);
            STARTING_RUNNING_HASH_OBJ = new HashObject(HashAlgorithm.SHA_384, runningHashStart.length, Bytes.wrap(runningHashStart));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Given a SingleTransactionRecord update its consensus timestamp and generate sidecar items */
    private static SingleTransactionRecord changeTransactionConsensusTimeAndGenerateSideCarItems(
            final Instant newConsensusTime,
            final SingleTransactionRecord singleTransactionRecord,
            final boolean generateSideCarItems) throws Exception {
        final Timestamp consensusTimestamp = Timestamp.newBuilder()
                .seconds(newConsensusTime.getEpochSecond())
                .nanos(newConsensusTime.getNano()).build();
        final Transaction transaction = singleTransactionRecord.transaction();
        final SignedTransaction signedTransaction = SignedTransaction.PROTOBUF.parse(BufferedData.wrap(transaction.signedTransactionBytes().toByteArray()));
        final TransactionBody transactionBody = TransactionBody.PROTOBUF.parse(BufferedData.wrap(signedTransaction.bodyBytes().toByteArray()));

        final TransactionBody newTransactionBody = transactionBody.copyBuilder()
                .transactionID(transactionBody.transactionID().copyBuilder().transactionValidStart(consensusTimestamp).build())
                .build();
        final SignedTransaction newSignedTransaction = signedTransaction.copyBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(newTransactionBody))
                .build();
        final Transaction newTransaction = transaction.copyBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(newSignedTransaction))
                .build();
        // update transaction record consensus timestamp
        final TransactionRecord newTransactionRecord = singleTransactionRecord.record().copyBuilder()
                .consensusTimestamp(consensusTimestamp)
                .build();
        // generate random number 0-5 of sidecar items
        final ArrayList<TransactionSidecarRecord> sidecarItems = new ArrayList<>();
        if (generateSideCarItems) {
            for (int j = 0; j < RANDOM.nextInt(5); j++) {
                sidecarItems.add(TransactionSidecarRecord.newBuilder()
                        .consensusTimestamp(consensusTimestamp)
                        .actions(ContractActions.newBuilder().contractActions(
                                ContractAction.newBuilder()
                                        .gas(RANDOM.nextInt(1000))
                                        .callType(ContractActionType.CALL)
                                        .input(randomBytes())
                                        .output(randomBytes())
                                        .build()))
                        .build());
            }
        }
        // return new SingleTransactionRecord
        return new SingleTransactionRecord(newTransaction, newTransactionRecord, sidecarItems);
    }

    /*

                final Timestamp consensusTimestamp = Timestamp.newBuilder()
                        .seconds(consensusTimestampBase.seconds() + i)
                        .nanos(consensusTimestampBase.nanos() + i).build();
                RecordStreamItem rsi = RecordStreamItem.newBuilder()
                        .record(
                                TransactionRecord.newBuilder()
                                        .consensusTimestamp(consensusTimestamp)
                                        .transactionID(
                                                TransactionID.newBuilder()
                                                        .transactionValidStart(Timestamp.newBuilder().seconds(RANDOM.nextLong(1000)).nanos(RANDOM.nextInt(1000)))
                                                        .accountID(AccountID.newBuilder().accountNum(RANDOM.nextLong()))
                                                        .nonce(RANDOM.nextInt())
                                        )
                        )
                        .transaction(Transaction.newBuilder()
                                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(
                                        SignedTransaction.newBuilder()
                                                .bodyBytes(TransactionBody.PROTOBUF.toBytes(TransactionBody.newBuilder()
                                                                .transactionID(TransactionID.newBuilder()
                                                                        .transactionValidStart(Timestamp.newBuilder().seconds(RANDOM.nextLong(1000)).nanos(RANDOM.nextInt(1000)))
                                                                        .accountID(AccountID.newBuilder().accountNum(RANDOM.nextLong()))
                                                                        .nonce(RANDOM.nextInt())
                                                                        .build())
                                                                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
                                                                        .topicID(TopicID.newBuilder().topicNum(RANDOM.nextLong()))
                                                                        .message(randomBytes())
                                                                        .build())
                                                        .build()))
                                                .build())))
                        .build();
                TEST_ITEMS.add(new SingleTransactionRecord(rsi.transaction(), rsi.record(), sidecarItems));
     */
}
