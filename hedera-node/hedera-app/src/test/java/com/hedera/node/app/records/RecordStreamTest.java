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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.RecordStreamFile;
import com.hedera.node.app.records.files.StreamFileProducerSingleThreaded;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.stream.RecordStreamFileWriter;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.node.app.service.mono.stream.RecordStreamType;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.PublicStores;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecordStreamTest {
    private static final byte[] EMPTY_ARRAY = new byte[] {};

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private VersionedConfiguration versionedConfiguration;

    @Mock
    private NodeInfo nodeInfo;

    private Signer signer;
    private EdECPrivateKey userPrivateKey;
    private EdECPublicKey userPublicKey;

    //    @TempDir
    private static final Path tempDir = Path.of("/Users/jasperpotts/Desktop/temp");

    /** Convert all the record files */
    public static void main3(String[] args) throws Exception {
        final URL recordFilesDirUrl = RecordStreamTest.class.getResource("/record-files");
        final Path recordFilesDirPath = Path.of(recordFilesDirUrl.toURI());
        final Path recordJsonFilesDirPath = tempDir.resolve("record-json-files");
        Files.createDirectories(recordJsonFilesDirPath);
        for (String recordFileName : RECORD_FILE_NAMES) {
            final Path recordJsonFilePath = recordJsonFilesDirPath.resolve(
                    recordFileName.substring(0, recordFileName.length() - ".rcd.gz".length()) + ".json");
            RecordStreamFile testRecordStreamFile =
                    parseDecompressedRecordStreamFile(loadRecordStreamFileDecompressedData(recordFileName));
            System.out.println("recordJsonFilePath = " + recordJsonFilePath);
            try (WritableStreamingData wsd = new WritableStreamingData(Files.newOutputStream(recordJsonFilePath))) {
                RecordStreamFile.JSON.write(testRecordStreamFile, wsd);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        //        final String recordFileName = RECORD_FILE_NAMES[5];
        final String recordFileName = "2023-05-01T00_00_24.038693760Z.rcd.gz";
        final String recordFileNameJson = "2023-05-01T00_00_24.038693760Z.json";

        final URL recordFileUrl = RecordStreamTest.class.getResource("/record-files/" + recordFileNameJson);
        final Path recordFilePath = Path.of(recordFileUrl.toURI());
        RecordStreamFile testRecordStreamFile =
                RecordStreamFile.JSON.parse(BufferedData.wrap(Files.readAllBytes(recordFilePath)));
        System.out.println("testRecordStreamFile = " + testRecordStreamFile);

        //        RecordStreamFile testRecordStreamFile = parseDecompressedRecordStreamFile(decompressedBytes);
        //        String json = RecordStreamFile.JSON.toJSON(testRecordStreamFile);
        //        byte [] newFile = jsonToCompressedRecordFile(json);
        //        byte [] newFileCompressed = jsonToCompressedRecordFileCompressed(json);

        final URL recordFileGzUrl = RecordStreamTest.class.getResource("/record-files/" + recordFileName);
        final Path recordFileGzPath = Path.of(recordFileGzUrl.toURI());
        System.out.println("recordFileGzPath = " + recordFileGzPath);
        System.out.println("recordFileGzPath size = " + Files.size(recordFileGzPath));

        byte[] readCompressedBytes = Files.readAllBytes(recordFileGzPath);

        // write JSON to Proto Compressed
        byte[] compressedJsonToProto = toCompressedRecordFile(testRecordStreamFile);
        System.out.println("readCompressedBytes.length = " + readCompressedBytes.length);
        System.out.println("compressedJsonToProto.length = " + compressedJsonToProto.length);
        System.out.println("Arrays.equals(readCompressedBytes, readCompressedBytes) = "
                + Arrays.equals(readCompressedBytes, compressedJsonToProto));

        //        try(GZIPOutputStream gout = new GZIPOutputStream(Files.newOutputStream(tempDir.resolve("old2.dat"))))
        // {
        //            gout.write(decompressedBytes);
        //        }
        //        try(GZIPOutputStream gout = new GZIPOutputStream(Files.newOutputStream(tempDir.resolve("new2.dat"))))
        // {
        //            gout.write(decompressedBytes);
        //        }
    }

    public static byte[] jsonToCompressedRecordFile(String json) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(new byte[] {0, 0, 0, 6}); // V6 int in little endian
        WritableStreamingData wsd = new WritableStreamingData(gout);
        BufferedData jsonBuffer = BufferedData.wrap(json.getBytes(StandardCharsets.UTF_8));
        RecordStreamFile.PROTOBUF.write(RecordStreamFile.JSON.parse(jsonBuffer), wsd);
        gout.flush();
        bout.flush();
        return bout.toByteArray();
    }

    public static byte[] toCompressedRecordFile(RecordStreamFile recordStreamFile) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(new byte[] {0, 0, 0, 6}); // V6 int in little endian
        WritableStreamingData wsd = new WritableStreamingData(gout);
        RecordStreamFile.PROTOBUF.write(recordStreamFile, wsd);
        gout.flush();
        bout.flush();
        gout.close();
        bout.close();
        return bout.toByteArray();
    }

    public static byte[] jsonToCompressedRecordFileCompressed(String json) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        GZIPOutputStream gout = new GZIPOutputStream(bout);
        gout.write(new byte[] {0, 0, 0, 6}); // V6 int in little endian
        WritableStreamingData wsd = new WritableStreamingData(gout);
        BufferedData jsonBuffer = BufferedData.wrap(json.getBytes(StandardCharsets.UTF_8));
        RecordStreamFile.PROTOBUF.write(RecordStreamFile.JSON.parse(jsonBuffer), wsd);
        gout.close();
        return bout.toByteArray();
    }

    @BeforeEach
    void setUp() throws Exception {
        // generate test user keys
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        userPrivateKey = (EdECPrivateKey) keyPair.getPrivate();
        userPublicKey = (EdECPublicKey) keyPair.getPublic();
        // generate node keys and signer
        final KeysAndCerts keysAndCerts = KeysAndCerts.generate(
                "a-name", userPrivateKey.getEncoded(), EMPTY_ARRAY, EMPTY_ARRAY, new PublicStores());
        signer = new PlatformSigner(keysAndCerts);
        // setup config
        final RecordStreamConfig recordStreamConfig = new RecordStreamConfig(
                true, tempDir.resolve("recordStreams").toString(), "sidecar", 2, 5000, false, 256, 6, 6, true, true);
        // setup mocks
        when(versionedConfiguration.getConfigData(RecordStreamConfig.class)).thenReturn(recordStreamConfig);
        when(configProvider.getConfiguration()).thenReturn(versionedConfiguration);
        when(nodeInfo.accountMemo()).thenReturn("test-node");
        //        when(signer.sign(any())).thenAnswer(bytes -> {
        //            Signature s = Signature.getInstance("SHA384withRSA", CryptoConstants.SIG_PROVIDER);
        //            s.initSign(keysAndCerts.sigKeyPair().getPrivate());
        //            return s.sign();
        //        });
    }

    @Test
    public void compareRecordFiles() throws Exception {
        final String recordFileName = RECORD_FILE_NAMES[5];
        RecordStreamFile testRecordStreamFile =
                parseDecompressedRecordStreamFile(loadRecordStreamFileDecompressedData(recordFileName));
        // write with old writer
        //        writeWithOldRecordStreamFileWriter(testRecordStreamFile);

        // set hapi version
        when(nodeInfo.hapiVersion()).thenReturn(testRecordStreamFile.hapiProtoVersion());
        // create StreamFileProducer
        StreamFileProducerSingleThreaded streamFileProducerSingleThreaded =
                new StreamFileProducerSingleThreaded(configProvider, nodeInfo, signer, null);
        // set the StreamFileProducer's starting running hash to match
        streamFileProducerSingleThreaded.setRunningHash(
                testRecordStreamFile.startObjectRunningHash().hash());
        // get the consensus time of the first transaction
        final var firstTransactionConsensusTime =
                testRecordStreamFile.recordStreamItems().get(0).record().consensusTimestamp();
        final var firstTransactionConsensusTimeInstant =
                Instant.ofEpochSecond(firstTransactionConsensusTime.seconds(), firstTransactionConsensusTime.nanos());
        // end old block
        streamFileProducerSingleThreaded.switchBlocks(
                testRecordStreamFile.blockNumber() - 1,
                testRecordStreamFile.blockNumber(),
                firstTransactionConsensusTimeInstant);
        // write items
        streamFileProducerSingleThreaded.writeRecordStreamItems(
                testRecordStreamFile.blockNumber(),
                firstTransactionConsensusTimeInstant,
                testRecordStreamFile.recordStreamItems().stream()
                        .map(recordStreamItem -> new SingleTransactionRecord(
                                recordStreamItem.transaction(), recordStreamItem.record(), Collections.emptyList())));
        // end block
        streamFileProducerSingleThreaded.switchBlocks(
                testRecordStreamFile.blockNumber(),
                testRecordStreamFile.blockNumber() + 1,
                firstTransactionConsensusTimeInstant.plus(1, ChronoUnit.HOURS));
        // compare the new written record file
        final byte[] originalBytes = loadRecordStreamFileDecompressedData(recordFileName);
        final byte[] newFileBytes = loadNewRecordStreamFileDecompressedData(tempDir, recordFileName);
        // parse new file again and compare final hashes
        RecordStreamFile newRecordStreamFile = parseDecompressedRecordStreamFile(newFileBytes);
        byte[] originalStartHash =
                testRecordStreamFile.startObjectRunningHash().hash().toByteArray();
        byte[] newStartHash =
                newRecordStreamFile.startObjectRunningHash().hash().toByteArray();
        byte[] originalEndHash =
                testRecordStreamFile.endObjectRunningHash().hash().toByteArray();
        byte[] newEndHash = newRecordStreamFile.endObjectRunningHash().hash().toByteArray();
        HexFormat hexFormat = HexFormat.of();
        System.out.println("originalStartHash = " + hexFormat.formatHex(originalStartHash));
        System.out.println("newStartHash      = " + hexFormat.formatHex(newStartHash));
        System.out.println("originalEndHash   = " + hexFormat.formatHex(originalEndHash));
        System.out.println("newEndHash        = " + hexFormat.formatHex(newEndHash));
        assertArrayEquals(
                testRecordStreamFile.endObjectRunningHash().hash().toByteArray(),
                newRecordStreamFile.endObjectRunningHash().hash().toByteArray());
        // compare all bytes
        assertArrayEquals(originalBytes, newFileBytes);
    }

    private void writeWithOldRecordStreamFileWriter(RecordStreamFile testRecordStreamFile) {
        try {
            Path testDir = tempDir.resolve("recordStreams2");
            Files.createDirectories(testDir);
            String path = testDir.toAbsolutePath().toString();
            System.out.println("RecordStreamTest.writeWithOldRecordStreamFileWriter");
            System.out.println("    path = " + path);
            GlobalDynamicProperties dynamicProperties = mock(GlobalDynamicProperties.class);
            when(dynamicProperties.shouldCompressRecordFilesOnCreation()).thenReturn(true);

            RecordStreamFileWriter recordStreamFileWriter = new RecordStreamFileWriter(
                    path,
                    signer,
                    Release038xStreamType.RELEASE_038x_STREAM_TYPE,
                    "sidecar",
                    1024 * 1024,
                    false,
                    File::delete,
                    mock(GlobalDynamicProperties.class));
            recordStreamFileWriter.setRunningHash(new Hash(
                    testRecordStreamFile.startObjectRunningHash().hash().toByteArray()));

            //
            //            var first = Mockito.mock(RecordStreamObject.class);
            //            when(first.closesCurrentFile()).thenReturn(true);
            //            System.out.println("first = " + first);
            //            System.out.println("first = " + first.closesCurrentFile());
            //
            //            recordStreamFileWriter.addObject(first);
            boolean first = true;
            for (var recordStreamItem : testRecordStreamFile.recordStreamItems()) {
                final var consensusTime = recordStreamItem.record().consensusTimestamp();
                final var consensusTimeInstant = Instant.ofEpochSecond(consensusTime.seconds(), consensusTime.nanos());
                final var recordStreamObject = new RecordStreamObject(
                                convert(recordStreamItem.record()),
                                convert(recordStreamItem.transaction()),
                                consensusTimeInstant,
                                Collections.emptyList())
                        .withBlockNumber(100);
                if (first) {
                    recordStreamObject.setWriteNewFile();
                    first = false;
                }
                recordStreamFileWriter.addObject(recordStreamObject);
            }
            recordStreamFileWriter.closeCurrentAndSign();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private com.hederahashgraph.api.proto.java.TransactionRecord convert(TransactionRecord tr) {
        try {
            return com.hederahashgraph.api.proto.java.TransactionRecord.parseFrom(
                    TransactionRecord.PROTOBUF.toBytes(tr).toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private com.hederahashgraph.api.proto.java.Transaction convert(Transaction tr) {
        try {
            return com.hederahashgraph.api.proto.java.Transaction.parseFrom(
                    Transaction.PROTOBUF.toBytes(tr).toByteArray());
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public enum Release038xStreamType implements RecordStreamType {
        RELEASE_038x_STREAM_TYPE;
        private static final int[] RELEASE_038x_FILE_HEADER = new int[] {5, 0, 38, 0};

        public int[] getFileHeader() {
            return RELEASE_038x_FILE_HEADER;
        }

        public byte[] getSigFileHeader() {
            return new byte[] {(byte) RELEASE_038x_FILE_HEADER[0]};
        }
    }

    private static void loadRecordStreamItems() {}

    public static void main2(String[] args) throws Exception {
        parseDecompressedRecordStreamFile(loadRecordStreamFileDecompressedData(RECORD_FILE_NAMES[5]));
    }

    private static byte[] loadRecordStreamFileDecompressedData(String resourceFileName) throws Exception {
        final URL recordFileUrl = RecordStreamTest.class.getResource("/record-files/" + resourceFileName);
        try (final GZIPInputStream in = new GZIPInputStream(Files.newInputStream(Path.of(recordFileUrl.toURI())))) {
            return in.readAllBytes();
        }
    }

    private static byte[] loadNewRecordStreamFileDecompressedData(Path tempDir, String resourceFileName)
            throws Exception {
        final Path recordFile = tempDir.resolve("recordStreams/recordtest-node/" + resourceFileName);
        try (final GZIPInputStream in = new GZIPInputStream(Files.newInputStream(recordFile))) {
            return in.readAllBytes();
        }
    }

    private static RecordStreamFile parseDecompressedRecordStreamFile(byte[] decompressedData) throws Exception {
        final BufferedData data = BufferedData.wrap(decompressedData);
        int version = data.readInt();
        System.out.println("version = " + version);
        return RecordStreamFile.PROTOBUF.parse(data);
    }

    private static void generateRecordStreamItemsOriginal() throws Exception {
        final URL recordFileUrl = RecordStreamTest.class.getResource("/record-files/" + RECORD_FILE_NAMES[5]);
        try (final ReadableStreamingData in =
                new ReadableStreamingData(new GZIPInputStream(Files.newInputStream(Path.of(recordFileUrl.toURI()))))) {
            int version = in.readInt();
            System.out.println("version = " + version);
            RecordStreamFile recordStreamFile = RecordStreamFile.PROTOBUF.parse(in);
            System.out.println("recordStreamFile = " + recordStreamFile);
        }
    }

    private static void generateRecordStreamItems2() throws Exception {
        final URL recordFileUrl = RecordStreamTest.class.getResource("/record-files/" + RECORD_FILE_NAMES[5]);
        try (final DataInputStream in =
                new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(recordFileUrl.toURI()))))) {
            int version = in.readInt();
            System.out.println("version = " + version);

            com.hedera.services.stream.proto.RecordStreamFile recordStreamFile =
                    com.hedera.services.stream.proto.RecordStreamFile.parseFrom(in);
            System.out.println("recordStreamFile = " + recordStreamFile);
        }
    }

    private static void generateRecordStreamItems3() throws Exception {
        final URL recordFileUrl = RecordStreamTest.class.getResource("/record-files/" + RECORD_FILE_NAMES[5]);
        try (final DataInputStream in =
                new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(recordFileUrl.toURI()))))) {
            int version = in.readInt();
            System.out.println("version = " + version);

            RecordStreamFile recordStreamFile = RecordStreamFile.PROTOBUF.parse(new ReadableStreamingData(in));
            System.out.println("recordStreamFile = " + recordStreamFile);
        }
    }

    //    private RecordStreamItem createRecordStreamItem() throws Exception {
    //        // create body bytes
    //        Bytes bodyBytes = PbjHelper.toBytes(TransactionBody.PROTOBUF,
    //                TransactionBody.newBuilder().build());
    //        // sign body bytes
    //        final KeysAndCerts keysAndCerts =
    //                KeysAndCerts.generate("a-name", userPrivateKey.getBytes().get(), EMPTY_ARRAY, EMPTY_ARRAY, new
    // PublicStores());
    //        Crypto crypto = new Crypto(keysAndCerts, ForkJoinPool.commonPool());
    //        com.swirlds.common.crypto.Signature signature = crypto.sign(PbjHelper.toByteArray(bodyBytes));
    //
    //        return RecordStreamItem.newBuilder()
    //                .transaction(Transaction.newBuilder()
    //                        .signedTransactionBytes(PbjHelper.toBytes(SignedTransaction.PROTOBUF,
    //                                SignedTransaction.newBuilder()
    //                                        .bodyBytes(bodyBytes)
    //                                        .sigMap(SignatureMap.newBuilder().sigPair(
    //                                                SignaturePair.newBuilder()
    //                                                        .pubKeyPrefix(ByteString.copyFromUtf8("prefix"))
    //                                                        .ed25519(ByteString.copyFromUtf8("ed25519"))
    //                                                        .build()
    //                                        ).build())
    //                                        ))
    //                                        )
    //                        )
    //                ).build();
    //    }

    private static final String[] RECORD_FILE_NAMES = new String[] {
        "2023-05-01T00_00_00.026034002Z.rcd.gz",
        "2023-05-01T00_00_02.007090003Z.rcd.gz",
        "2023-05-01T00_00_04.009493141Z.rcd.gz",
        "2023-05-01T00_00_06.001537614Z.rcd.gz",
        "2023-05-01T00_00_08.015149365Z.rcd.gz",
        "2023-05-01T00_00_10.006970916Z.rcd.gz",
        "2023-05-01T00_00_12.006119307Z.rcd.gz",
        "2023-05-01T00_00_14.019111677Z.rcd.gz",
        "2023-05-01T00_00_16.032283003Z.rcd.gz",
        "2023-05-01T00_00_18.009856003Z.rcd.gz",
        "2023-05-01T00_00_20.010132003Z.rcd.gz",
        "2023-05-01T00_00_22.017528662Z.rcd.gz",
        "2023-05-01T00_00_24.038693760Z.rcd.gz",
        "2023-05-01T00_00_26.033807003Z.rcd.gz",
        "2023-05-01T00_00_28.046782404Z.rcd.gz",
        "2023-05-01T00_00_30.000468348Z.rcd.gz",
        "2023-05-01T00_00_32.005239840Z.rcd.gz",
        "2023-05-01T00_00_34.001170390Z.rcd.gz",
        "2023-05-01T00_00_36.010099776Z.rcd.gz",
        "2023-05-01T00_00_38.000276347Z.rcd.gz",
        "2023-05-01T00_00_40.175771376Z.rcd.gz",
        "2023-05-01T00_00_42.002199306Z.rcd.gz",
        "2023-05-01T00_00_44.010791793Z.rcd.gz",
        "2023-05-01T00_00_46.005320797Z.rcd.gz",
        "2023-05-01T00_00_48.061314003Z.rcd.gz",
        "2023-05-01T00_00_50.046198028Z.rcd.gz",
        "2023-05-01T00_00_52.012844607Z.rcd.gz",
        "2023-05-01T00_00_54.013882003Z.rcd.gz",
        "2023-05-01T00_00_56.024406003Z.rcd.gz",
        "2023-05-01T00_00_58.002672003Z.rcd.gz",
        "2023-05-01T00_01_00.024519003Z.rcd.gz",
        "2023-05-01T00_01_02.067237585Z.rcd.gz",
        "2023-05-01T00_01_04.029861115Z.rcd.gz",
        "2023-05-01T00_01_06.115672003Z.rcd.gz",
        "2023-05-01T00_01_08.010768406Z.rcd.gz",
        "2023-05-01T00_01_10.011760003Z.rcd.gz",
        "2023-05-01T00_01_12.025986051Z.rcd.gz",
        "2023-05-01T00_01_14.026348003Z.rcd.gz",
        "2023-05-01T00_01_16.017167003Z.rcd.gz",
        "2023-05-01T00_01_18.004855846Z.rcd.gz",
        "2023-05-01T00_01_20.007865557Z.rcd.gz",
        "2023-05-01T00_01_22.060767323Z.rcd.gz",
        "2023-05-01T00_01_24.004769003Z.rcd.gz",
        "2023-05-01T00_01_26.023143678Z.rcd.gz",
        "2023-05-01T00_01_28.091736755Z.rcd.gz",
        "2023-05-01T00_01_30.029157003Z.rcd.gz",
        "2023-05-01T00_01_32.005273341Z.rcd.gz",
        "2023-05-01T00_01_34.004217351Z.rcd.gz",
        "2023-05-01T00_01_36.023431663Z.rcd.gz",
        "2023-05-01T00_01_38.010767749Z.rcd.gz"
    };
}
