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

package com.hedera.node.app.records;

import static com.hedera.node.app.records.files.RecordStreamV6Verifier.validateRecordStreamFiles;
import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.files.RecordFileFormatV6;
import com.hedera.node.app.records.files.RecordFileReaderV6;
import com.hedera.node.app.records.files.StreamFileProducerBase;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked", "DataFlowIssue"})
public class BlockRecordManagerTest {
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private @Mock ConfigProvider configProvider;
    private @Mock VersionedConfiguration versionedConfiguration;
    private @Mock NodeInfo nodeInfo;
    private @Mock WritableStates blockManagerWritableStates;
    private @Mock HederaState hederaState;
    private @Mock WorkingStateAccessor workingStateAccessor;
    private WritableSingletonState<BlockInfo> blockInfoWritableSingletonState;
    private WritableSingletonState<RunningHashes> runningHashesWritableSingletonState;

    /** Temporary in memory file system used for testing */
    private FileSystem fs;
    //
    //    public static void main(String[] args) throws IOException {
    //        Path recordsDir = Path.of("/Users/jasperpotts/Desktop/Downloaded Record Stream Files/");
    //        Files.list(recordsDir)
    //                .filter(path -> path.toString().endsWith(".rcd.gz"))
    //                .map(path -> {
    //                    String fileName = path.getFileName().toString();
    //                    fileName = fileName.substring(0, fileName.length() - ".rcd.gz".length());
    //                    return getTimeStampFromFileName(fileName);
    //                })
    //                .sorted()
    //                .forEach(time -> {
    //                    System.out.println("time = " + time);
    //                    System.out.println("    seconds = " + time.getEpochSecond());
    //                    System.out.println("    nano = " + time.getNano());
    //                    System.out.println("    period = " + getPeriod(time,2000));
    //
    //                });
    //    }
    //
    //    public static void main(String[] args) {
    //        TEST_BLOCKS.stream()
    //                .forEach(block -> {
    //                    var time = Instant.ofEpochSecond(block.get(0).record().consensusTimestamp().seconds(),
    // block.get(0).record().consensusTimestamp().nanos());
    //                    System.out.println(time+"    count     = " + block.size());
    ////                    System.out.println("    newPeriod = " + (time.getEpochSecond() / 2));
    ////                    System.out.println("    period    = " + getPeriod(time,2000));
    //
    //                });
    ////        TEST_BLOCKS.stream()
    ////                .flatMap(List::stream)
    ////                .map(item ->
    ////                        Instant.ofEpochSecond(item.record().consensusTimestamp().seconds(),
    // item.record().consensusTimestamp().nanos()))
    ////                .forEach(time -> {
    //////                    System.out.println("time = " + time);
    //////                    System.out.println("    seconds = " + time.getEpochSecond());
    //////                    System.out.println("    nano = " + time.getNano());
    ////                    System.out.println("    period = " + getPeriod(time,2000));
    ////                });
    //    }
    // 841449612 -> 841449620

    @BeforeEach
    void setUpEach() throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        // setup config
        final RecordStreamConfig recordStreamConfig =
                new RecordStreamConfig(true, tempDir.toString(), "sidecar", 2, 5000, false, 256, 6, 6, true, true);
        // setup mocks
        when(versionedConfiguration.getConfigData(RecordStreamConfig.class)).thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(VERSION);
        // create a BlockInfo state
        blockInfoWritableSingletonState = new BlockInfoWritableSingletonState();
        // create a RunningHashes state
        runningHashesWritableSingletonState = new RunningHashesWritableSingletonState();
        // setup initial block info, pretend that previous block was 2 seconds before first test transaction
        blockInfoWritableSingletonState.put(new BlockInfo(
                BLOCK_NUM - 1,
                new Timestamp(
                        TEST_BLOCKS.get(0).get(0).record().consensusTimestamp().seconds() - 2, 0),
                STARTING_RUNNING_HASH_OBJ.hash()));
        when(blockManagerWritableStates.getSingleton(BlockRecordService.BLOCK_INFO_STATE_KEY))
                .thenReturn((WritableSingletonState) blockInfoWritableSingletonState);
        // setup initial running hashes
        runningHashesWritableSingletonState.put(new RunningHashes(STARTING_RUNNING_HASH_OBJ.hash(), null, null, null));
        when(blockManagerWritableStates.getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY))
                .thenReturn((WritableSingletonState) runningHashesWritableSingletonState);
        // setup hedera state to get blockManagerWritableStates
        when(hederaState.createWritableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);
        when(hederaState.createReadableStates(BlockRecordService.NAME)).thenReturn(blockManagerWritableStates);
        // setup workingStateAccessor to get hedera state
        when(workingStateAccessor.getHederaState()).thenReturn(hederaState);
    }

    @AfterEach
    public void shutdown() throws Exception {
        fs.close();
    }

    @Test
    public void BlockRecordManager() throws Exception {
        Bytes finalRunningHash;
        try (BlockRecordManager blockRecordManager =
                new BlockRecordManagerImpl(configProvider, nodeInfo, SIGNER, fs, workingStateAccessor)) {
            // check starting hash, we need to be using the correct starting hash for the tests to work
            assertEquals(
                    STARTING_RUNNING_HASH_OBJ.hash().toHex(),
                    blockRecordManager.getRunningHash().toHex());

            // write a blocks & record files
            int transactionCount = 0;
            for (int i = 0; i < TEST_BLOCKS.size(); i++) {
                final var blockData = TEST_BLOCKS.get(i);
                final var block = BLOCK_NUM + i;
                for (var record : blockData) {
                    blockRecordManager.startUserTransaction(
                            fromTimestamp(record.record().consensusTimestamp()), hederaState);
                    blockRecordManager.endUserTransaction(Stream.of(record), hederaState);
                    transactionCount++;
                    // pretend rounds happen every 20 transactions
                    if (transactionCount % 20 == 0) {
                        blockRecordManager.endRound(hederaState);
                    }
                }
                // TODO validate block info
                assertEquals(block - 1, blockRecordManager.lastBlockNo());
            }
            System.out.println("transactionCount = " + transactionCount);
            // end the last round
            blockRecordManager.endRound(hederaState);
            // collect info for later validation
            finalRunningHash = blockRecordManager.getRunningHash();
            // try with resources will close the blockRecordManager and result in waiting for background threads to
            // finish and close any open files. No collect block record manager info to be validated
        }
        // check running hash
        assertEquals(
                computeRunningHash(
                                STARTING_RUNNING_HASH_OBJ.hash(),
                                TEST_BLOCKS.stream().flatMap(List::stream).toList())
                        .toHex(),
                finalRunningHash.toHex());

        // print out all files
        try (var pathStream = Files.walk(fs.getPath("/temp"))) {
            pathStream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (file.getFileName().toString().endsWith("Z.rcd.gz")) {
                        try {
                            int count = RecordFileReaderV6.read(file)
                                    .recordStreamItems()
                                    .size();
                            System.out.println(
                                    file.toAbsolutePath() + " - (" + Files.size(file) + ")   count = " + count);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        System.out.println(file.toAbsolutePath() + " - (" + Files.size(file) + ")");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        // check record files
        final var recordStreamConfig = versionedConfiguration.getConfigData(RecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir()).resolve("record" + nodeInfo.accountMemo()),
                recordStreamConfig,
                USER_PUBLIC_KEY,
                TEST_BLOCKS,
                BLOCK_NUM);
    }

    /** Given a list of items and a starting hash calculate the running hash at the end */
    private Bytes computeRunningHash(
            final Bytes startingHash, final List<SingleTransactionRecord> transactionRecordList) throws Exception {
        return RecordFileFormatV6.INSTANCE.computeNewRunningHash(
                startingHash,
                transactionRecordList.stream()
                        .map(str -> RecordFileFormatV6.INSTANCE.serialize(str, BLOCK_NUM, VERSION))
                        .toList());
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    private interface StreamFileProducerSupplier {
        StreamFileProducerBase get(
                @NonNull final ConfigProvider configProvider,
                @NonNull final NodeInfo nodeInfo,
                @NonNull final Signer signer,
                @Nullable final FileSystem fileSystem);
    }

    /**
     * Block Info Writeable Singleton State Implementation
     */
    private static class BlockInfoWritableSingletonState implements WritableSingletonState<BlockInfo> {
        private BlockInfo blockInfo;

        @Override
        public void put(@Nullable BlockInfo value) {
            this.blockInfo = value;
        }

        @Override
        public boolean isModified() {
            return true;
        }

        @NotNull
        @Override
        public String getStateKey() {
            return BlockRecordService.BLOCK_INFO_STATE_KEY;
        }

        @Nullable
        @Override
        public BlockInfo get() {
            return blockInfo;
        }

        @Override
        public boolean isRead() {
            return true;
        }
    }

    /**
     * Running Hashes Writeable Singleton State Implementation
     */
    private static class RunningHashesWritableSingletonState implements WritableSingletonState<RunningHashes> {
        private RunningHashes runningHashes;

        @Override
        public void put(@Nullable RunningHashes value) {
            this.runningHashes = value;
        }

        @Override
        public boolean isModified() {
            return true;
        }

        @NotNull
        @Override
        public String getStateKey() {
            return BlockRecordService.BLOCK_INFO_STATE_KEY;
        }

        @Nullable
        @Override
        public RunningHashes get() {
            return runningHashes;
        }

        @Override
        public boolean isRead() {
            return true;
        }
    }
    ;
}
