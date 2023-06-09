package com.hedera.node.app.records.files;

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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hedera.node.app.records.RecordTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SuppressWarnings("DataFlowIssue")
@ExtendWith(MockitoExtension.class)
public class StreamFileProducerTest {
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private @Mock ConfigProvider configProvider;
    private @Mock VersionedConfiguration versionedConfiguration;
    private @Mock NodeInfo nodeInfo;
    private static Signer signer;
    private static EdECPrivateKey userPrivateKey;
    private static EdECPublicKey userPublicKey;

    private FileSystem fs;
    private Path tempDir;
//    private Path tempDir = Path.of("/Users/jasperpotts/Desktop/temp");

    @BeforeAll
    static void setUp() throws Exception {
        // generate test user keys
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        userPrivateKey = (EdECPrivateKey)keyPair.getPrivate();
        userPublicKey = (EdECPublicKey)keyPair.getPublic();
        // generate node keys and signer
        final KeysAndCerts keysAndCerts =
                KeysAndCerts.generate("a-name", userPrivateKey.getEncoded(), EMPTY_ARRAY, EMPTY_ARRAY, new PublicStores());
        signer = new PlatformSigner(keysAndCerts);
    }

    @BeforeEach
    void setUpEach() throws Exception {
        // create in memory temp dir
        fs = Jimfs.newFileSystem(Configuration.unix());
        tempDir = fs.getPath("/temp");
        Files.createDirectory(tempDir);

        Files.writeString(tempDir.resolve("0.0.0"), "0.0.0");

        // setup config
        final RecordStreamConfig recordStreamConfig = new RecordStreamConfig(
                true,
                tempDir.toString(),
                "sidecar",
                2,
                5000,
                false,
                256,
                6,
                6,
                true,
                true
        );
        // setup mocks
        when(versionedConfiguration.getConfigData(RecordStreamConfig.class)).thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        when(nodeInfo.hapiVersion()).thenReturn(VERSION);
    }

    public static Stream<Arguments> streamFileProducers() {
        ForkJoinPool forkJoinPool = new ForkJoinPool(
                4,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                (t,e) -> e.printStackTrace(), true);
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(4,4,20, TimeUnit.SECONDS,new LinkedBlockingQueue<>());

        StreamFileProducerSupplier singleThreaded = StreamFileProducerSingleThreaded::new;
        StreamFileProducerSupplier concurrent = (configProvider, nodeInfo, signer, fs) -> new StreamFileProducerConcurrent(
                configProvider, nodeInfo, signer, fs, threadPoolExecutor);
        return Stream.of(
                Arguments.of(singleThreaded)
                ,
                Arguments.of(concurrent)
        );
    }


    @ParameterizedTest
    @MethodSource("streamFileProducers")
    public void streamFileProducer(final StreamFileProducerSupplier streamFileProducerSupplier) throws Exception {
        StreamFileProducerBase streamFileProducer = streamFileProducerSupplier.get(configProvider, nodeInfo, signer, fs);
        streamFileProducer.setRunningHash(STARTING_RUNNING_HASH_OBJ.hash());
        long block = BLOCK_NUM - 1;
        // write a blocks & record files
        for (var blockData : TEST_BLOCKS) {
            block ++;
            final Instant blockFirstTransactionTimestamp = fromTimestamp(blockData.get(0).record().consensusTimestamp());
            streamFileProducer.switchBlocks(block - 1, block, blockFirstTransactionTimestamp);
            streamFileProducer.writeRecordStreamItems(block, blockFirstTransactionTimestamp, blockData.stream());
        }
        // send a switchBlocks to close the last block
        streamFileProducer.switchBlocks(
                block-1,
                block,
                fromTimestamp(TEST_BLOCKS.get(TEST_BLOCKS.size()-1).get(0).record().consensusTimestamp())
                        .plus(2, ChronoUnit.SECONDS));
        // check running hash
        assertEquals(
                computeRunningHash(
                        STARTING_RUNNING_HASH_OBJ.hash(),
                        TEST_BLOCKS.stream().flatMap(List::stream).toList()).toHex(),
                streamFileProducer.getCurrentRunningHash().toHex());
        // wait for all threads to finish
        streamFileProducer.waitForComplete();
        // start running hashes
        Bytes runningHash = STARTING_RUNNING_HASH_OBJ.hash();
        // now check the generated record files

        for (int i = 0; i < TEST_BLOCKS.size(); i++) {
            var blockData = TEST_BLOCKS.get(i);
            boolean hashSidecars = TEST_BLOCKS_WITH_SIDECARS[i];
            var firstBlockTimestamp = fromTimestamp(blockData.get(0).record().consensusTimestamp());
            Path recordFile1 = streamFileProducer.getRecordFilePath(firstBlockTimestamp);
            runningHash = validateRecordFile(
                    i,
                    runningHash,
                    recordFile1,
                    hashSidecars ? streamFileProducer.getSidecarFilePath(firstBlockTimestamp, 1) : null,
                    SignatureFileWriter.getSigFilePath(recordFile1),
                    blockData);
        }
    }

    /**
     * Check the record file, sidecar file and signature file exist and are not empty. Validate their contents and
     * return the running hash at the end of the file.
     */
    private static Bytes validateRecordFile(final int blockIndex,
                                            final Bytes startingRunningHash,
                                            final Path recordFilePath,
                                           final Path sidecarFilePath,
                                           final Path recordFileSigPath,
                                           final List<SingleTransactionRecord> transactionRecordList) throws Exception {
        // check record file
        assertTrue(Files.exists(recordFilePath), "expected record file ["+recordFilePath+"] in blockIndex["+blockIndex+"] to exist");
        assertTrue(Files.size(recordFilePath) > 0, "expected record file ["+recordFilePath+"] in blockIndex["+blockIndex+"] to not be empty");
        var recordFile  = RecordFileReaderV6.read(recordFilePath);
        RecordFileReaderV6.validateHashes(recordFile);
        assertEquals(startingRunningHash.toHex(),
                recordFile.startObjectRunningHash().hash().toHex(),
                "expected record file start running hash to be correct, blockIndex["+blockIndex+"]");
        // check sideCar file
        if (sidecarFilePath != null) {
            assertTrue(Files.exists(sidecarFilePath), "expected side car file ["+sidecarFilePath+"] in blockIndex["+blockIndex+"] to exist");
            assertTrue(Files.size(sidecarFilePath) > 0, "expected side car file ["+sidecarFilePath+"] in blockIndex["+blockIndex+"] to not be empty");
        }
        // check signature file
        assertTrue(Files.exists(recordFileSigPath), "expected signature file ["+recordFileSigPath+"] in blockIndex["+blockIndex+"] to exist");
        assertTrue(Files.size(recordFileSigPath) > 0, "expected signature file ["+recordFileSigPath+"] in blockIndex["+blockIndex+"] to not be empty");
        // return running hash
        return recordFile.endObjectRunningHash().hash();
    }

    /** Given a list of items and a starting hash calculate the running hash at the end */
    private Bytes computeRunningHash(final Bytes startingHash, final List<SingleTransactionRecord> transactionRecordList) throws Exception {
        return RecordFileFormatV6.INSTANCE.computeNewRunningHash(startingHash, transactionRecordList
                    .stream()
                    .map(str -> RecordFileFormatV6.INSTANCE.serialize(str, BLOCK_NUM, VERSION))
                    .toList());
    }

    private static Instant fromTimestamp(final Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    private interface StreamFileProducerSupplier {
        StreamFileProducerBase get(@NonNull final ConfigProvider configProvider,
                                   @NonNull final NodeInfo nodeInfo,
                                   @NonNull final Signer signer,
                                   @Nullable final FileSystem fileSystem);
    }
}
