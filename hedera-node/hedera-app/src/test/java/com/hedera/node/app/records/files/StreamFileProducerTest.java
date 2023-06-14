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

import static com.hedera.node.app.records.files.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.records.RecordStreamConfig;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.PublicStores;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("DataFlowIssue")
@ExtendWith(MockitoExtension.class)
public class StreamFileProducerTest {
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private @Mock ConfigProvider configProvider;
    private @Mock VersionedConfiguration versionedConfiguration;
    private @Mock NodeInfo nodeInfo;
    private static Signer signer;
    private static EdECPublicKey userPublicKey;

    /** Temporary in memory file system used for testing */
    private FileSystem fs;

    @BeforeAll
    static void setUp() throws Exception {
        // generate test user keys
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        EdECPrivateKey userPrivateKey = (EdECPrivateKey) keyPair.getPrivate();
        userPublicKey = (EdECPublicKey) keyPair.getPublic();
        // generate node keys and signer
        final KeysAndCerts keysAndCerts = KeysAndCerts.generate(
                "a-name", userPrivateKey.getEncoded(), EMPTY_ARRAY, EMPTY_ARRAY, new PublicStores());
        signer = new PlatformSigner(keysAndCerts);
    }

    void setUpEach(int sidecarMaxSizeMb) throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        // setup config
        final RecordStreamConfig recordStreamConfig = new RecordStreamConfig(
                true, tempDir.toString(), "sidecar", 2, 5000, false, sidecarMaxSizeMb, 6, 6, true, true);
        // setup mocks
        when(versionedConfiguration.getConfigData(RecordStreamConfig.class)).thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(VERSION);
    }

    @AfterEach
    public void shutdown() throws Exception {
        fs.close();
    }

    /** BlockRecordManager mostly writes records one at a time so simulate that here */
    @ParameterizedTest
    @CsvSource({
        "StreamFileProducerSingleThreaded, 256",
        "StreamFileProducerConcurrent,256",
        "StreamFileProducerConcurrent,1"
    })
    public void oneTransactionAtATime(final String streamFileProducerClassName, int sidecarMaxSizeMb) throws Exception {
        setUpEach(sidecarMaxSizeMb);
        doTestCommon(
                streamFileProducerClassName, (streamFileProducer, blockData, block, blockFirstTransactionTimestamp) -> {
                    for (var record : blockData) {
                        streamFileProducer.writeRecordStreamItems(
                                block, blockFirstTransactionTimestamp, Stream.of(record));
                    }
                });
    }

    /** It is also interesting to test as larger batches because in theory 1 user transaction could be 1000 transactions */
    @ParameterizedTest
    @CsvSource({
        "StreamFileProducerSingleThreaded, 256",
        "StreamFileProducerConcurrent,256",
        "StreamFileProducerConcurrent,1"
    })
    public void batchOfTransactions(final String streamFileProducerClassName, int sidecarMaxSizeMb) throws Exception {
        setUpEach(sidecarMaxSizeMb);
        doTestCommon(
                streamFileProducerClassName,
                (streamFileProducer, blockData, block, blockFirstTransactionTimestamp) ->
                        streamFileProducer.writeRecordStreamItems(
                                block, blockFirstTransactionTimestamp, blockData.stream()));
    }

    /**
     * Common test implementation for streamFileProducer and batchOfTransactions
     *
     * @param streamFileProducerClassName the class name for the StreamFileProducer
     * @param blockWriter the block writer to use
     */
    private void doTestCommon(final String streamFileProducerClassName, BlockWriter blockWriter) throws Exception {
        Bytes finalRunningHash;
        try (StreamFileProducerBase streamFileProducer =
                switch (streamFileProducerClassName) {
                    case "StreamFileProducerSingleThreaded" -> new StreamFileProducerSingleThreaded(
                            configProvider, nodeInfo, signer, fs);
                    case "StreamFileProducerConcurrent" -> new StreamFileProducerConcurrent(
                            configProvider, nodeInfo, signer, fs, ForkJoinPool.commonPool());
                    default -> throw new IllegalArgumentException(
                            "Unknown streamFileProducerClassName: " + streamFileProducerClassName);
                }) {
            streamFileProducer.setRunningHash(STARTING_RUNNING_HASH_OBJ.hash());
            System.out.println("STARTING_RUNNING_HASH_OBJ = "
                    + STARTING_RUNNING_HASH_OBJ.hash().toHex());
            System.out.println(
                    "start 1 = " + streamFileProducer.getCurrentRunningHash().toHex());
            long block = BLOCK_NUM - 1;
            // write a blocks & record files
            for (var blockData : TEST_BLOCKS) {
                block++;
                final Instant blockFirstTransactionTimestamp =
                        fromTimestamp(blockData.get(0).record().consensusTimestamp());
                streamFileProducer.switchBlocks(block - 1, block, blockFirstTransactionTimestamp);
                blockWriter.write(streamFileProducer, blockData, block, blockFirstTransactionTimestamp);
            }
            // collect final running hash
            finalRunningHash = streamFileProducer.getCurrentRunningHash();
        }

        // check running hash
        System.out.println("END HASH = " + finalRunningHash.toHex());
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
                    System.out.println(file.toAbsolutePath() + " - (" + Files.size(file) + ")");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        // check record files
        final var recordStreamConfig = versionedConfiguration.getConfigData(RecordStreamConfig.class);
        validateRecordStreamFiles(
                fs.getPath(recordStreamConfig.logDir()).resolve("record" + nodeInfo.accountMemo()),
                fs.getPath(recordStreamConfig.logDir())
                        .resolve("record" + nodeInfo.accountMemo())
                        .resolve(recordStreamConfig.sidecarDir()),
                true,
                userPublicKey);
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

    /** Interface for BlockWriter used for lambdas in tests */
    private interface BlockWriter {
        void write(
                @NonNull final StreamFileProducerBase streamFileProducer,
                @NonNull final List<SingleTransactionRecord> blockData,
                final long block,
                @Nullable final Instant blockFirstTransactionTimestamp);
    }
}
