/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.files;

import static com.hedera.node.app.records.files.StreamFileProducerBase.COMPRESSION_ALGORITHM_EXTENSION;
import static com.hedera.node.app.records.files.StreamFileProducerBase.RECORD_EXTENSION;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.convertInstantToStringWithPadding;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.*;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.interfaces.EdECPublicKey;
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

    private static Bytes randomBytes(int numBytes) {
        final byte[] bytes = new byte[numBytes];
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
    public static final boolean[] TEST_BLOCKS_WITH_SIDECARS =
            new boolean[] {false, true, true, true, false, true, false, false, true};
    //    block seconds       24,   26,   28,   30,    32,   34,    36,    38,   40

    static {
        try {
            final List<List<SingleTransactionRecord>> testBlocks = new ArrayList<>();
            // load real record stream items from a JSON resource file
            final Path jsonPath = Path.of(RecordStreamV6Test.class
                    .getResource("/record-files/2023-05-01T00_00_24.038693760Z.json")
                    .toURI());
            final RecordStreamFile recordStreamFile =
                    RecordStreamFile.JSON.parse(new ReadableStreamingData(Files.newInputStream(jsonPath)));
            final List<SingleTransactionRecord> realRecordStreamItems = recordStreamFile.recordStreamItems().stream()
                    .map(item ->
                            new SingleTransactionRecord(item.transaction(), item.record(), Collections.emptyList()))
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
                    SingleTransactionRecord item =
                            realRecordStreamItems.get(RANDOM.nextInt(realRecordStreamItems.size()));
                    items.add(changeTransactionConsensusTimeAndGenerateSideCarItems(
                            consenusTime, item, generateSidecarItems));
                    consenusTime = consenusTime.plusNanos(10);
                }
                testBlocks.add(items);
            }
            TEST_BLOCKS = Collections.unmodifiableList(testBlocks);
            // validate that all generated blocks have valid consensus time and transactions are in correct blocks
            TEST_BLOCKS.forEach(block -> {
                var time = Instant.ofEpochSecond(
                        block.get(0).record().consensusTimestamp().seconds(),
                        block.get(0).record().consensusTimestamp().nanos());
                var period = getPeriod(time, 2000);
                var count = block.stream()
                        .map(item -> Instant.ofEpochSecond(
                                item.record().consensusTimestamp().seconds(),
                                item.record().consensusTimestamp().nanos()))
                        .filter(time2 -> getPeriod(time2, 2000) != period)
                        .count();
                System.out.println("count = " + count);
                assert count == 0 : "Found at least one transaction in wrong block, count = " + count;
            });
            // create a random hash for running hash start
            MessageDigest messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            byte[] randomBytes = new byte[100];
            RANDOM.nextBytes(randomBytes);
            messageDigest.update(randomBytes);
            final byte[] runningHashStart = messageDigest.digest();
            STARTING_RUNNING_HASH = new Hash(runningHashStart, DigestType.SHA_384);
            STARTING_RUNNING_HASH_OBJ =
                    new HashObject(HashAlgorithm.SHA_384, runningHashStart.length, Bytes.wrap(runningHashStart));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Given a SingleTransactionRecord update its consensus timestamp and generate sidecar items */
    private static SingleTransactionRecord changeTransactionConsensusTimeAndGenerateSideCarItems(
            final Instant newConsensusTime,
            final SingleTransactionRecord singleTransactionRecord,
            final boolean generateSideCarItems)
            throws Exception {
        final Timestamp consensusTimestamp = Timestamp.newBuilder()
                .seconds(newConsensusTime.getEpochSecond())
                .nanos(newConsensusTime.getNano())
                .build();
        final Transaction transaction = singleTransactionRecord.transaction();
        final SignedTransaction signedTransaction = SignedTransaction.PROTOBUF.parse(
                BufferedData.wrap(transaction.signedTransactionBytes().toByteArray()));
        final TransactionBody transactionBody = TransactionBody.PROTOBUF.parse(
                BufferedData.wrap(signedTransaction.bodyBytes().toByteArray()));

        final TransactionBody newTransactionBody = transactionBody
                .copyBuilder()
                .transactionID(transactionBody
                        .transactionID()
                        .copyBuilder()
                        .transactionValidStart(consensusTimestamp)
                        .build())
                .build();
        final SignedTransaction newSignedTransaction = signedTransaction
                .copyBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(newTransactionBody))
                .build();
        final Transaction newTransaction = transaction
                .copyBuilder()
                .signedTransactionBytes(SignedTransaction.PROTOBUF.toBytes(newSignedTransaction))
                .build();
        // update transaction record consensus timestamp
        final TransactionRecord newTransactionRecord = singleTransactionRecord
                .record()
                .copyBuilder()
                .consensusTimestamp(consensusTimestamp)
                .build();
        // generate random number 0-5 of sidecar items
        final ArrayList<TransactionSidecarRecord> sidecarItems = new ArrayList<>();
        if (generateSideCarItems) {
            for (int j = 0; j < RANDOM.nextInt(5); j++) {
                sidecarItems.add(TransactionSidecarRecord.newBuilder()
                        .consensusTimestamp(consensusTimestamp)
                        .actions(ContractActions.newBuilder()
                                .contractActions(ContractAction.newBuilder()
                                        .gas(RANDOM.nextInt(1000))
                                        .callType(ContractActionType.CALL)
                                        .input(randomBytes(1_000))
                                        .output(randomBytes(1_000))
                                        .build()))
                        .build());
            }
        }
        // return new SingleTransactionRecord
        return new SingleTransactionRecord(newTransaction, newTransactionRecord, sidecarItems);
    }

    /** Validate record stream generated with the test data in this file */
    public static void validateRecordStreamFiles(
            Path recordsDir, Path sidecarsDir, boolean compressed, EdECPublicKey userPublicKey) throws Exception {
        // start running hashes
        Bytes runningHash = STARTING_RUNNING_HASH_OBJ.hash();
        // now check the generated record files

        for (int i = 0; i < TEST_BLOCKS.size(); i++) {
            final var blockData = TEST_BLOCKS.get(i);
            final boolean hasSidecars = TEST_BLOCKS_WITH_SIDECARS[i];
            final Instant firstBlockTimestamp =
                    fromTimestamp(blockData.get(0).record().consensusTimestamp());
            final Path recordFile1 = getRecordFilePath(recordsDir, firstBlockTimestamp, compressed);
            runningHash = validateRecordFile(
                    i,
                    firstBlockTimestamp,
                    runningHash,
                    recordFile1,
                    sidecarsDir,
                    hasSidecars,
                    SignatureFileWriter.getSigFilePath(recordFile1),
                    blockData,
                    compressed);
            // TODO validate signature file
        }
    }

    /**
     * Check the record file, sidecar file and signature file exist and are not empty. Validate their contents and
     * return the running hash at the end of the file.
     */
    public static Bytes validateRecordFile(
            final int blockIndex,
            final Instant firstBlockTimestamp,
            final Bytes startingRunningHash,
            final Path recordFilePath,
            final Path sidecarsDir,
            final boolean hasSideCars,
            final Path recordFileSigPath,
            final List<SingleTransactionRecord> transactionRecordList,
            boolean compressed)
            throws Exception {
        // check record file
        assertTrue(
                Files.exists(recordFilePath),
                "expected record file [" + recordFilePath + "] in blockIndex[" + blockIndex + "] to exist");
        assertTrue(
                Files.size(recordFilePath) > 0,
                "expected record file [" + recordFilePath + "] in blockIndex[" + blockIndex + "] to not be empty");
        var recordFile = RecordFileReaderV6.read(recordFilePath);
        RecordFileReaderV6.validateHashes(recordFile);
        assertEquals(
                startingRunningHash.toHex(),
                recordFile.startObjectRunningHash().hash().toHex(),
                "expected record file start running hash to be correct, blockIndex[" + blockIndex
                        + "], recordFilePath=[" + recordFilePath + "]");
        // check sideCar file
        if (hasSideCars) {
            // TODO find way to validate all side cars, for now check first one exists and is not empty
            final Path sidecarFilePath = getSidecarFilePath(sidecarsDir, firstBlockTimestamp, compressed, 1);
            assertTrue(
                    Files.exists(sidecarFilePath),
                    "expected side car file [" + sidecarFilePath + "] in blockIndex[" + blockIndex + "] to exist");
            assertTrue(
                    Files.size(sidecarFilePath) > 0,
                    "expected side car file [" + sidecarFilePath + "] in blockIndex[" + blockIndex
                            + "] to not be empty");
        }
        // check signature file
        assertTrue(
                Files.exists(recordFileSigPath),
                "expected signature file [" + recordFileSigPath + "] in blockIndex[" + blockIndex + "] to exist");
        assertTrue(
                Files.size(recordFileSigPath) > 0,
                "expected signature file [" + recordFileSigPath + "] in blockIndex[" + blockIndex
                        + "] to not be empty");
        // return running hash
        return recordFile.endObjectRunningHash().hash();
    }

    /** Given a list of items and a starting hash calculate the running hash at the end */
    public Bytes computeRunningHash(
            final Bytes startingHash, final List<SingleTransactionRecord> transactionRecordList) {
        return RecordFileFormatV6.INSTANCE.computeNewRunningHash(
                startingHash,
                transactionRecordList.stream()
                        .map(str -> RecordFileFormatV6.INSTANCE.serialize(str, BLOCK_NUM, VERSION))
                        .toList());
    }

    public static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    /**
     * Get the record file path for a record file with the given consensus time
     *
     * @param consensusTime  a consensus timestamp of the first object to be written in the file
     * @return Path to a record file for that consensus time
     */
    private static Path getRecordFilePath(
            @NonNull final Path nodeScopedRecordLogDir,
            @NonNull final Instant consensusTime,
            final boolean compressFilesOnCreation) {
        return nodeScopedRecordLogDir.resolve(convertInstantToStringWithPadding(consensusTime) + "." + RECORD_EXTENSION
                + (compressFilesOnCreation ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    }

    /**
     * Get full sidecar file path from given Instant object
     *
     * @param currentRecordFileFirstTransactionConsensusTimestamp the consensus timestamp of the first transaction in the
     *                                                            record file this sidecar file is associated with
     * @param sidecarId                                           the sidecar id of this sidecar file
     * @return the new sidecar file path
     */
    protected static Path getSidecarFilePath(
            @NonNull final Path nodeScopedSidecarDir,
            @NonNull final Instant currentRecordFileFirstTransactionConsensusTimestamp,
            final boolean compressFilesOnCreation,
            final int sidecarId) {
        return nodeScopedSidecarDir.resolve(
                convertInstantToStringWithPadding(currentRecordFileFirstTransactionConsensusTimestamp)
                        + "_"
                        + String.format("%02d", sidecarId)
                        + "."
                        + RECORD_EXTENSION
                        + (compressFilesOnCreation ? COMPRESSION_ALGORITHM_EXTENSION : ""));
    }
}
