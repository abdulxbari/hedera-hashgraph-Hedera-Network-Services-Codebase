package com.hedera.services.stream;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.SignatureType;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TestFileUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
import com.swirlds.common.stream.Signer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateSigFilePath;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.common.stream.StreamAligned.NO_ALIGNMENT;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class RecordStreamFileWriterTest {
	RecordStreamFileWriterTest() {
	}

	@BeforeEach
	void setUp() throws NoSuchAlgorithmException {
		subject = new RecordStreamFileWriter(
				expectedExportDir(),
				logPeriodMs,
				signer,
				false,
				streamType,
				expectedExportDir());
		messageDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);
	}

	@Test
	void recordAndSignatureFilesAreCreatedAsExpected() throws IOException, NoSuchAlgorithmException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		given(streamType.getSidecarExtension()).willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
		final var firstBlockEntireFileSignature = "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var firstBlockMetadataSignature = "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var secondBlockEntireFileSignature = "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		final var secondBlockMetadataSignature = "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(firstBlockEntireFileSignature)
				.willReturn(firstBlockMetadataSignature)
				.willReturn(secondBlockEntireFileSignature)
				.willReturn(secondBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 26, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);

		// when
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(4, 1, firstTransactionInstant, true);
		final var secondBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(8, 2,
				firstTransactionInstant.plusSeconds(logPeriodMs / 1000), true);
		final var thirdBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 3,
				firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000), true);
		Stream.of(firstBlockRSOs, secondBlockRSOs, thirdBlockRSOs)
				.flatMap(Collection::stream)
				.forEach(subject::addObject);

		// then
		assertRecordStreamFiles(
				1L,
				firstBlockRSOs,
				startRunningHash,
				firstBlockEntireFileSignature,
				firstBlockMetadataSignature);
		assertRecordStreamFiles(
				2L,
				secondBlockRSOs,
				firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash(),
				secondBlockEntireFileSignature,
				secondBlockMetadataSignature);
	}

	@Test
	void objectsFromFirstPeriodAreNotExternalizedWhenStartWriteAtCompleteWindowIsTrue()
			throws IOException, NoSuchAlgorithmException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		final var secondBlockEntireFileSignature = "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		final var secondBlockMetadataSignature = "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(secondBlockEntireFileSignature)
				.willReturn(secondBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);
		subject.setStartWriteAtCompleteWindow(true);

		// when
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(2, 1, firstTransactionInstant, true);
		final var secondBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(5, 2,
				firstTransactionInstant.plusSeconds(logPeriodMs / 1000), true);
		final var thirdBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 3,
				firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000), true);
		Stream.of(firstBlockRSOs, secondBlockRSOs, thirdBlockRSOs)
				.flatMap(Collection::stream)
				.forEach(subject::addObject);

		// then
		assertFalse(Path.of(subject.generateRecordFilePath(firstTransactionInstant)).toFile().exists());
		assertRecordStreamFiles(
				2L,
				secondBlockRSOs,
				firstBlockRSOs.get(firstBlockRSOs.size() - 1).getRunningHash().getHash(),
				secondBlockEntireFileSignature,
				secondBlockMetadataSignature);
	}

	@Test
	void objectsFromDifferentPeriodsButWithSameAlignmentAreExternalizedInSameFile()
			throws IOException, NoSuchAlgorithmException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		final var firstBlockEntireFileSignature = "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var firstBlockMetadataSignature = "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(firstBlockEntireFileSignature)
				.willReturn(firstBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 9, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);

		// when
		// generate 2 RSOs for block #1, where the second RSO is in different period, but with same alignment (block)
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant,
				true);
		firstBlockRSOs.addAll(generateNRecordStreamObjectsForBlockMStartingFromT(1, 1,
				firstTransactionInstant.plusSeconds(2 * (logPeriodMs / 1000)), true));
		// RSOs for second block to trigger externalization of first block
		final var secondBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 2,
				firstTransactionInstant.plusSeconds(3 * (logPeriodMs / 1000)), true);
		Stream.of(firstBlockRSOs, secondBlockRSOs)
				.flatMap(Collection::stream)
				.forEach(subject::addObject);

		// then
		assertRecordStreamFiles(
				1L,
				firstBlockRSOs,
				startRunningHash,
				firstBlockEntireFileSignature,
				firstBlockMetadataSignature);
	}

	@Test
	void alignmentIsIgnoredForObjectsWithNoAlignment() throws IOException, NoSuchAlgorithmException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getSigFileHeader()).willReturn(SIG_FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		final var firstBlockEntireFileSignature = "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var firstBlockMetadataSignature = "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(firstBlockEntireFileSignature)
				.willReturn(firstBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 10, 24, 16, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);

		// when
		// generate 2 RSOs for block #1 without alignment; should be externalized in same record file
		final var firstBlockRSOs =
				generateNRecordStreamObjectsForBlockMStartingFromT(2, NO_ALIGNMENT, firstTransactionInstant, true);
		// generate 1 RSO in next block to trigger externalization of previous file; even though alignments are equal,
		// when they are NO_ALIGNMENT, we ignore it and start a new file regardless
		final var secondBlockRSOs =
				generateNRecordStreamObjectsForBlockMStartingFromT(1, NO_ALIGNMENT,
						firstTransactionInstant.plusSeconds(4 * (logPeriodMs / 1000)), true);
		Stream.of(firstBlockRSOs, secondBlockRSOs)
				.flatMap(Collection::stream)
				.forEach(subject::addObject);

		// then
		assertRecordStreamFiles(
				NO_ALIGNMENT,
				firstBlockRSOs,
				startRunningHash,
				firstBlockEntireFileSignature,
				firstBlockMetadataSignature);
	}


	private List<RecordStreamObject> generateNRecordStreamObjectsForBlockMStartingFromT(
			final int numberOfRSOs,
			final long blockNumber,
			final Instant firstBlockTransactionInstant,
			final boolean addSidecars)
	{
		final var recordStreamObjects = new ArrayList<RecordStreamObject>();
		for (int i = 0; i < numberOfRSOs; i++) {
			final var timestamp =
					Timestamp.newBuilder()
							.setSeconds(firstBlockTransactionInstant.getEpochSecond())
							.setNanos(1000 * i);
			final ExpirableTxnRecord.Builder expirableBuilder = ExpirableTxnRecord.newBuilder()
					.setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())));
			final var transactionRecord =
					TransactionRecord.newBuilder().setConsensusTimestamp(timestamp);
			final var transaction =
					Transaction.newBuilder()
							.setSignedTransactionBytes(ByteString.copyFrom(
									("block #" + blockNumber + ", transaction #" + i).getBytes(StandardCharsets.UTF_8)));

			List<TransactionSidecarRecord.Builder> sidecars;
			if (addSidecars) {
				final var stateChangeSidecar = TransactionSidecarRecord.newBuilder()
						.setStateChanges(ContractStateChanges.newBuilder().addContractStateChanges(ContractStateChange.newBuilder()
								.setContractId(IdUtils.asContract("0.0." + blockNumber))
								.addStorageChanges(StorageChange.newBuilder()
										.setSlot(ByteString.copyFrom(new byte[]{(byte) i}))
										.setValueRead(ByteString.copyFrom(new byte[]{(byte) i}))
										.build())
								.build()));
				final var contractActionSidecar =
						TransactionSidecarRecord.newBuilder().setActions(ContractActions.newBuilder().addContractActions(ContractAction.newBuilder()
								.setInput(ByteString.copyFrom("randomText" + blockNumber, StandardCharsets.UTF_8)).build()));
				final var bytecodeSidecar = TransactionSidecarRecord.newBuilder().setBytecode(
						ContractBytecode.newBuilder()
								.setContractId(IdUtils.asContract("0.0." + blockNumber))
								.setInitcode(ByteString.copyFrom("thatsTheInitCode", StandardCharsets.UTF_8))
								.build());
				sidecars = List.of(stateChangeSidecar, contractActionSidecar, bytecodeSidecar);
			} else {
				sidecars = List.of();
			}
			final var recordStreamObject =
					new RecordStreamObject(
							expirableBuilder.build(),
							transaction.build(),
							Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
							sidecars
					);
			final var hashInput = recordStreamObject.toString().getBytes(StandardCharsets.UTF_8);
			recordStreamObject.getRunningHash().setHash(new Hash(messageDigest.digest(hashInput)));
			recordStreamObject.withBlockNumber(blockNumber);
			recordStreamObjects.add(recordStreamObject);
		}
		return recordStreamObjects;
	}

	private void assertRecordStreamFiles(
			final long expectedBlock,
			final List<RecordStreamObject> blockRSOs,
			final Hash startRunningHash,
			final byte[] expectedEntireFileSignature,
			final byte[] expectedMetadataSignature
	) throws IOException, NoSuchAlgorithmException {
		final var firstTxnTimestamp = blockRSOs.get(0).getTimestamp();
		final var recordStreamFilePath =
				subject.generateRecordFilePath(
						Instant.ofEpochSecond(firstTxnTimestamp.getEpochSecond(), firstTxnTimestamp.getNano()));
		final var recordStreamFilePair = RecordStreamingUtils.readRecordStreamFile(recordStreamFilePath);

		assertEquals(RECORD_STREAM_VERSION, recordStreamFilePair.getLeft());
		final var recordStreamFileOptional = recordStreamFilePair.getRight();
		assertTrue(recordStreamFileOptional.isPresent());
		final var recordStreamFile = recordStreamFileOptional.get();

		assertRecordFile(expectedBlock,
				blockRSOs,
				startRunningHash,
				recordStreamFile,
				new File(recordStreamFilePath)
		);
		assertSignatureFile(
				recordStreamFilePath,
				expectedEntireFileSignature,
				expectedMetadataSignature,
				recordStreamFilePair.getLeft(),
				recordStreamFile
		);
	}

	private void assertRecordFile(
			final long expectedBlock,
			final List<RecordStreamObject> blockRSOs,
			final Hash startRunningHash,
			final RecordStreamFile recordStreamFile,
			final File recordFile
	) throws IOException, NoSuchAlgorithmException {
		assertTrue(logCaptor.debugLogs().contains("Stream file created " + recordFile.getName()));

		// assert HAPI semantic version
		assertEquals(recordStreamFile.getHapiProtoVersion(), SemanticVersion.newBuilder()
				.setMajor(FILE_HEADER_VALUES[1])
				.setMinor(FILE_HEADER_VALUES[2])
				.setPatch(FILE_HEADER_VALUES[3]).build()
		);

		// assert startRunningHash
		assertEquals(toProto(startRunningHash), recordStreamFile.getStartObjectRunningHash());

		assertTrue(logCaptor.debugLogs().contains("beginNew :: write startRunningHash to metadata " + startRunningHash));

		// assert RSOs
		assertEquals(blockRSOs.size(), recordStreamFile.getRecordStreamItemsCount());
		final var recordFileObjectsList = recordStreamFile.getRecordStreamItemsList();
		for (int i = 0; i < blockRSOs.size(); i++) {
			final var expectedRSO = blockRSOs.get(i);
			final var actualRSOProto = recordFileObjectsList.get(i);
			assertEquals(expectedRSO.getTransaction(), actualRSOProto.getTransaction());
			assertEquals(expectedRSO.getTransactionRecord(), actualRSOProto.getRecord());
		}

		// assert endRunningHash - should be the hash of the last RSO from the block
		final var expectedHashInput =
				blockRSOs.get(blockRSOs.size() - 1).toString().getBytes(StandardCharsets.UTF_8);
		final var expectedEndRunningHash = new Hash(messageDigest.digest(expectedHashInput));
		assertEquals(toProto(expectedEndRunningHash), recordStreamFile.getEndObjectRunningHash());

		assertTrue(logCaptor.debugLogs().contains("closeCurrentAndSign :: write endRunningHash "
				+ expectedEndRunningHash));

		// assert block number
		assertEquals(expectedBlock, recordStreamFile.getBlockNumber());
		assertTrue(logCaptor.debugLogs().contains("closeCurrentAndSign :: write block number " + expectedBlock));

		// assert sidecar metadata
		final var sidecarMetadataList = recordStreamFile.getSidecarsList();
		for (final var sidecarMetadata : sidecarMetadataList) {
			assertEquals(1, sidecarMetadata.getTypesCount());
			final var sidecarType = sidecarMetadata.getTypesList().get(0);
			String pathToSidecarFile = null;
			final var firstTxnTimestamp = blockRSOs.get(0).getTimestamp();
			final var firstTxnInstant =
					Instant.ofEpochSecond(firstTxnTimestamp.getEpochSecond(), firstTxnTimestamp.getNano());
			if (sidecarType.equals(SidecarType.CONTRACT_STATE_CHANGE)) {
				pathToSidecarFile = subject.generateSidecarFilePath(firstTxnInstant,
						RecordStreamType.SidecarType.STATE_CHANGES.getSidecarId());
			} else if (sidecarType.equals(SidecarType.CONTRACT_ACTION)) {
				pathToSidecarFile = subject.generateSidecarFilePath(firstTxnInstant,
						RecordStreamType.SidecarType.ACTIONS.getSidecarId());
			} else if (sidecarType.equals(SidecarType.CONTRACT_BYTECODE)) {
				pathToSidecarFile = subject.generateSidecarFilePath(firstTxnInstant,
						RecordStreamType.SidecarType.BYTECODES.getSidecarId());
			}
			assertNotNull(pathToSidecarFile);
			final var sidecarFileOptional = RecordStreamingUtils.readSidecarFile(pathToSidecarFile);
			assertTrue(sidecarFileOptional.isPresent());
			assertAllExpectedSidecarsAreInFile(sidecarFileOptional.get(), blockRSOs, sidecarType);
			final var expectedSidecarHash =
					LinkedObjectStreamUtilities.computeEntireHash(new File(pathToSidecarFile));
			final var actualSidecarHash = sidecarMetadata.getHash();
			assertEquals(HashAlgorithm.SHA_384, actualSidecarHash.getAlgorithm());
			assertEquals(expectedSidecarHash.getDigestType().digestLength(), actualSidecarHash.getLength());
			assertArrayEquals(expectedSidecarHash.getValue(), actualSidecarHash.getHash().toByteArray());
		}

		assertTrue(logCaptor.debugLogs().contains("Stream file written successfully " + recordFile.getName()));
	}

	private void assertAllExpectedSidecarsAreInFile(
			final SidecarFile sidecarFile,
			final List<RecordStreamObject> blockRSOs,
			final SidecarType sidecarType
	) {
		final var actualSidecarRecordsList = sidecarFile.getSidecarRecordsList();
		final var expectedSidecarRecordsList = new ArrayList<>();
		for (final var rso : blockRSOs) {
			if (isNotEmpty(rso.getSidecars())) {
				for (final var tsr : rso.getSidecars()) {
					if (tsr.hasActions() && sidecarType.equals(SidecarType.CONTRACT_ACTION)) {
						expectedSidecarRecordsList.add(tsr.build());
					} else if (tsr.hasStateChanges() && sidecarType.equals(SidecarType.CONTRACT_STATE_CHANGE)) {
						expectedSidecarRecordsList.add(tsr.build());
					} else if (tsr.hasBytecode() && sidecarType.equals(SidecarType.CONTRACT_BYTECODE)) {
						expectedSidecarRecordsList.add(tsr.build());
					}
				}
			}
		}
		assertEquals(expectedSidecarRecordsList, actualSidecarRecordsList);
	}

	private void assertSignatureFile(
			final String recordStreamFilePath,
			final byte[] expectedEntireFileSignature,
			final byte[] expectedMetadataSignature,
			final Integer recordStreamVersion,
			final RecordStreamFile recordStreamFileProto
	) throws IOException, NoSuchAlgorithmException {
		final var recordStreamFile = new File(recordStreamFilePath);
		final var signatureFilePath = generateSigFilePath(recordStreamFile);
		final var signatureFilePair = RecordStreamingUtils.readSignatureFile(signatureFilePath);
		assertEquals(RECORD_STREAM_VERSION, signatureFilePair.getLeft());

		final var signatureFileOptional = signatureFilePair.getRight();
		assertTrue(signatureFileOptional.isPresent());
		final var signatureFile = signatureFileOptional.get();

		/* --- assert entire file signature --- */
		final var entireFileSignatureObject = signatureFile.getFileSignature();
		// assert entire file hash
		final var expectedEntireHash = LinkedObjectStreamUtilities.computeEntireHash(recordStreamFile);
		final var actualEntireHash = entireFileSignatureObject.getHashObject();
		assertEquals(HashAlgorithm.SHA_384, actualEntireHash.getAlgorithm());
		assertEquals(expectedEntireHash.getDigestType().digestLength(), actualEntireHash.getLength());
		assertArrayEquals(expectedEntireHash.getValue(), actualEntireHash.getHash().toByteArray());
		// assert entire file signature
		assertEquals(SignatureType.SHA_384_WITH_RSA, entireFileSignatureObject.getType());
		assertEquals(expectedEntireFileSignature.length, entireFileSignatureObject.getLength());
		assertEquals(101 - expectedEntireFileSignature.length, entireFileSignatureObject.getChecksum());
		assertArrayEquals(expectedEntireFileSignature, entireFileSignatureObject.getSignature().toByteArray());

		/* --- assert metadata signature --- */
		final var expectedMetaHash = computeMetadataHashFrom(recordStreamVersion, recordStreamFileProto);
		final var metadataSignatureObject = signatureFile.getMetadataSignature();
		final var actualMetaHash = metadataSignatureObject.getHashObject();
		// assert metadata hash
		assertEquals(HashAlgorithm.SHA_384, actualMetaHash.getAlgorithm());
		assertEquals(expectedMetaHash.getDigestType().digestLength(), actualMetaHash.getLength());
		assertArrayEquals(expectedMetaHash.getValue(), actualMetaHash.getHash().toByteArray());
		// assert metadata signature
		assertEquals(SignatureType.SHA_384_WITH_RSA, metadataSignatureObject.getType());
		assertEquals(expectedMetadataSignature.length, metadataSignatureObject.getLength());
		assertEquals(101 - expectedMetadataSignature.length, metadataSignatureObject.getChecksum());
		assertArrayEquals(expectedMetadataSignature, metadataSignatureObject.getSignature().toByteArray());

		assertTrue(logCaptor.debugLogs().contains(
				"closeCurrentAndSign :: signature file saved: " + signatureFilePath));
	}

	private HashObject toProto(final Hash hash) {
		return HashObject.newBuilder()
				.setAlgorithm(HashAlgorithm.SHA_384)
				.setLength(hash.getDigestType().digestLength())
				.setHash(ByteString.copyFrom(hash.getValue()))
				.build();
	}

	private Hash computeMetadataHashFrom(final Integer version, final RecordStreamFile recordStreamFile) {
		try (final var outputStream = new SerializableDataOutputStream(new HashingOutputStream(messageDigest))) {
			// digest file header
			outputStream.writeInt(version);
			final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
			outputStream.writeInt(hapiProtoVersion.getMajor());
			outputStream.writeInt(hapiProtoVersion.getMinor());
			outputStream.writeInt(hapiProtoVersion.getPatch());

			// digest startRunningHash
			final var startRunningHash =
					new Hash(recordStreamFile.getStartObjectRunningHash().getHash().toByteArray(), DigestType.SHA_384);
			outputStream.write(startRunningHash.getValue());

			// digest endRunningHash
			final var endRunningHash =
					new Hash(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray(), DigestType.SHA_384);
			outputStream.write(endRunningHash.getValue());

			// digest block number
			outputStream.writeLong(recordStreamFile.getBlockNumber());

			return new Hash(messageDigest.digest(), DigestType.SHA_384);
		} catch (IOException e) {
			return new Hash("error".getBytes(StandardCharsets.UTF_8));
		}
	}

	@Test
	void clearCalledInMiddleOfWritingRecordFileSucceeds() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// send RSOs for block 1
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(4, 1, firstTransactionInstant,
				true);
		firstBlockRSOs.forEach(subject::addObject);

		// when
		subject.clear();

		// then
		assertTrue(logCaptor.debugLogs().contains("RecordStreamFileWriter::clear executed."));
	}

	@Test
	void clearCalledWhenNotWritingFileSucceeds() {
		// when
		subject.clear();

		// then
		assertThat(logCaptor.debugLogs(),
				contains(Matchers.startsWith("RecordStreamFileWriter::clear executed.")));
	}

	@Test
	void clearCatchesIOExceptionWhenClosingStreamsAndLogsIt() {
		try (final var ignored = Mockito.mockConstruction(
				SerializableDataOutputStream.class, (mock, context) -> doThrow(IOException.class).when(mock).close())
		) {
			// given
			given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
			final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
			// send RSOs for block 1
			generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant, true)
					.forEach(subject::addObject);

			// when
			subject.clear();

			// then
			assertThat(logCaptor.warnLogs(),
					contains(Matchers.startsWith("RecordStreamFileWriter::clear Exception in closing dosMeta")));
			assertTrue(logCaptor.debugLogs().contains("RecordStreamFileWriter::clear executed."));
		}
	}

	@Test
	void closeSucceeds() {
		// given
		final var subjectSpy = Mockito.spy(subject);

		// when
		subjectSpy.close();

		// then
		verify(subjectSpy).closeCurrentAndSign();
		assertThat(logCaptor.debugLogs(),
				contains(Matchers.startsWith("RecordStreamFileWriter finished writing the last object, is stopped")));
	}

	@Test
	void writingBlockNumberToMetadataIOEExceptionIsCaughtAndLoggedProperlyAndThreadInterrupted() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);

		// when
		try (MockedConstruction<SerializableDataOutputStream> ignored = Mockito.mockConstruction(
				SerializableDataOutputStream.class,
				(mock, context) -> doThrow(IOException.class).when(mock).writeLong(anyLong()))
		) {
			sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);
		}

		// then
		assertTrue(Thread.currentThread().isInterrupted());
		assertThat(logCaptor.warnLogs(),
				contains(Matchers.startsWith(
						"closeCurrentAndSign :: IOException when serializing endRunningHash and block number into " +
								"metadata")));
	}

	@Test
	void logAndDontDoAnythingWhenStreamFileAlreadyExists() throws IOException {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 1, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);
		final var expectedRecordFileName = generateStreamFileNameFromInstant(firstTransactionInstant, streamType);
		final var recordFile = new File(expectedExportDir() + File.separator + expectedRecordFileName).createNewFile();
		assertTrue(recordFile);

		// when
		sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);

		// then
		assertTrue(logCaptor.debugLogs().contains("Stream file already exists " + expectedRecordFileName));
	}

	private void sendRSOsForBlock1And2StartingFrom(Instant firstTransactionInstant) {
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant,
				true);
		final var secondBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 2,
				firstTransactionInstant.plusSeconds(2 * logPeriodMs / 1000), true);

		// when
		Stream.of(firstBlockRSOs, secondBlockRSOs)
				.flatMap(Collection::stream)
				.forEach(subject::addObject);
	}


	@Test
	void interruptThreadAndLogWhenIOExceptionIsCaughtWhileWritingSidecarRecordFile() {
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getExtension()).willReturn(RecordStreamType.RECORD_EXTENSION);
		given(streamType.getSidecarExtension()).willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
		final var firstTransactionInstant = LocalDateTime.of(2022, 11, 13, 11, 1, 55).toInstant(ZoneOffset.UTC);
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant,
				true);
		firstBlockRSOs.forEach(subject::addObject);

		try (MockedConstruction<SerializableDataOutputStream> ignored = Mockito.mockConstruction(
				SerializableDataOutputStream.class,
				(mock, context) -> doThrow(IOException.class).when(mock).write(any()))
		) {
			subject.closeCurrentAndSign();
			assertTrue(Thread.currentThread().isInterrupted());
			assertThat(logCaptor.warnLogs(),
					contains(Matchers.startsWith("closeCurrentAndSign :: IOException when creating sidecar files")));
		}
	}

	@Test
	void interruptThreadAndLogWhenNoAlgorithmExceptionIsCaughtWhileWritingSidecarRecordFile() {
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		given(streamType.getSidecarExtension()).willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
		final var firstTransactionInstant = LocalDateTime.of(2022, 6, 19, 15, 2, 55).toInstant(ZoneOffset.UTC);
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant,
				true);
		firstBlockRSOs.forEach(subject::addObject);

		try (final MockedStatic<LinkedObjectStreamUtilities> mockedStatic = mockStatic(LinkedObjectStreamUtilities.class))
		{
			mockedStatic.when(() -> LinkedObjectStreamUtilities.computeEntireHash(any(File.class)))
					.thenThrow(NoSuchAlgorithmException.class);

			subject.closeCurrentAndSign();
			assertTrue(Thread.currentThread().isInterrupted());
			assertThat(logCaptor.warnLogs(),
					contains(Matchers.startsWith("closeCurrentAndSign :: NoSuchAlgorithmException when creating sidecar files")));
		}
	}

	@Test
	void interruptThreadAndLogWhenIOExceptionIsCaughtWhileWritingRecordFile() {
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 1, 3, 21, 2, 55).toInstant(ZoneOffset.UTC);
		final var firstBlockRSOs = generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant, false);
		firstBlockRSOs.forEach(subject::addObject);

		try (MockedConstruction<SerializableDataOutputStream> ignored = Mockito.mockConstruction(
				SerializableDataOutputStream.class,
				(mock, context) -> doThrow(IOException.class).when(mock).writeInt(anyInt()))
		) {
			subject.closeCurrentAndSign();
			assertTrue(Thread.currentThread().isInterrupted());
			assertThat(logCaptor.warnLogs(),
					contains(Matchers.startsWith("closeCurrentAndSign :: IOException when serializing ")));
		}
	}

	@Test
	void waitingForStartRunningHashInterruptedExceptionIsCaughtAndLoggedProperly() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 4, 29, 11, 2, 55).toInstant(ZoneOffset.UTC);
		subject.clearRunningHash();

		// when
		Thread.currentThread().interrupt();
		generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant, true)
				.forEach(subject::addObject);

		// then
		assertTrue(logCaptor.errorLogs().get(0)
				.startsWith("beginNew :: Exception when getting startRunningHash for writing to metadata stream"));
	}

	@Test
	void waitingForEndRunningHashInterruptedExceptionIsCaughtAndLoggedProperly() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 4, 29, 11, 2, 55).toInstant(ZoneOffset.UTC);
		generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant, true)
				.forEach(subject::addObject);

		// when
		Thread.currentThread().interrupt();
		subject.closeCurrentAndSign();

		// then
		assertTrue(
				logCaptor.errorLogs().get(0).startsWith("closeCurrentAndSign :: failed when getting endRunningHash "));
	}

	@Test
	void exceptionWhenWritingSignatureFileIsCaughtAndLogged() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstBlockEntireFileSignature = "entireSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var firstBlockMetadataSignature = "metadataSignatureBlock1".getBytes(StandardCharsets.UTF_8);
		final var secondBlockEntireFileSignature = "entireSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		final var secondBlockMetadataSignature = "metadataSignatureBlock2".getBytes(StandardCharsets.UTF_8);
		given(signer.sign(any()))
				.willReturn(firstBlockEntireFileSignature)
				.willReturn(firstBlockMetadataSignature)
				.willReturn(secondBlockEntireFileSignature)
				.willReturn(secondBlockMetadataSignature);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 11, 16, 2, 55).toInstant(ZoneOffset.UTC);

		// when
		try (final MockedStatic<LinkedObjectStreamUtilities> mockedStatic =
					 mockStatic(LinkedObjectStreamUtilities.class)) {
			mockedStatic.when(() -> LinkedObjectStreamUtilities.generateSigFilePath(any(File.class)))
					.thenReturn("non/existent/directory");
			mockedStatic.when(() -> LinkedObjectStreamUtilities.getPeriod(any(Instant.class), anyLong()))
					.thenCallRealMethod();
			mockedStatic.when(() -> LinkedObjectStreamUtilities.computeEntireHash(any(File.class)))
					.thenCallRealMethod();

			sendRSOsForBlock1And2StartingFrom(firstTransactionInstant);
		}

		// then
		assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("closeCurrentAndSign ::  :: Fail to " +
				"generate signature file for")));
	}

	@Test
	void interruptAndLogWhenWritingStartRunningHashToMetadataStreamThrowsIOException() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 24, 11, 2, 55).toInstant(ZoneOffset.UTC);

		// when
		try (MockedConstruction<SerializableDataOutputStream> ignored = Mockito.mockConstruction(
				SerializableDataOutputStream.class,
				(mock, context) -> doThrow(IOException.class).when(mock).write(any()))
		) {
			generateNRecordStreamObjectsForBlockMStartingFromT(1, 1, firstTransactionInstant, false).forEach(
					subject::addObject);
		}

		// then
		assertTrue(Thread.currentThread().isInterrupted());
		assertThat(logCaptor.errorLogs(),
				contains(Matchers.startsWith("beginNew :: Got IOException when writing startRunningHash to")));
	}


	@Test
	void transactionSidecarWithNoActualSidecarLogsWarning() {
		// given
		given(streamType.getFileHeader()).willReturn(FILE_HEADER_VALUES);
		final var firstTransactionInstant = LocalDateTime.of(2022, 5, 26, 11, 2, 55).toInstant(ZoneOffset.UTC);
		// set initial running hash
		messageDigest.digest("yumyum".getBytes(StandardCharsets.UTF_8));
		final var startRunningHash = new Hash(messageDigest.digest());
		subject.setRunningHash(startRunningHash);

		// when
		final var faultyRSO = new RecordStreamObject(
				ExpirableTxnRecord.newBuilder().build(),
				Transaction.getDefaultInstance(),
				firstTransactionInstant,
				List.of(TransactionSidecarRecord.newBuilder())
		);
		subject.addObject(faultyRSO);

		// then
		assertThat(logCaptor.warnLogs(),
				contains(Matchers.equalTo("A sidecar record without an actual sidecar has been received")));
	}

	@Test
	void sidecarFileNameIsCorrectWithIdOf1Digit() {
		given(streamType.getSidecarExtension()).willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
		final var instant = Instant.parse("2022-05-26T11:02:55.000000000Z");


		final var actualSidecarFileName = subject.generateSidecarFilePath(instant, 5);

		final var expected =
				expectedExportDir() + File.separator + "2022-05-26T11_02_55.000000000Z_05." + streamType.getSidecarExtension();
		assertEquals(expected, actualSidecarFileName);
	}

	@Test
	void sidecarFileNameIsCorrectWithIdOf2Digits() {
		given(streamType.getSidecarExtension()).willReturn(RecordStreamType.SIDECAR_RECORD_EXTENSION);
		final var instant = Instant.parse("2022-05-26T11:02:55.000000000Z");

		final var actualSidecarFileName = subject.generateSidecarFilePath(instant, 10);

		final var expected =
				expectedExportDir() + File.separator + "2022-05-26T11_02_55.000000000Z_10." + streamType.getSidecarExtension();
		assertEquals(expected, actualSidecarFileName);
	}

	@BeforeAll
	static void beforeAll() {
		final var file = new File(expectedExportDir());
		if (!file.exists()) {
			assertTrue(file.mkdir());
		}
	}

	@AfterAll
	static void afterAll() throws IOException {
		TestFileUtils.blowAwayDirIfPresent(expectedExportDir());
	}

	private static String expectedExportDir() {
		return dynamicProperties.pathToBalancesExportDir() + File.separator + "recordStreamWriterTest";
	}

	private final static long logPeriodMs = 2000L;
	private static final int RECORD_STREAM_VERSION = 6;
	private static final int[] FILE_HEADER_VALUES = {
			RECORD_STREAM_VERSION,
			0,  // HAPI proto major version
			27, // HAPI proto minor version
			0   // HAPI proto patch version
	};
	private static final byte[] SIG_FILE_HEADER_VALUES = {
			RECORD_STREAM_VERSION,
	};

	@Mock
	private RecordStreamType streamType;
	@Mock
	private Signer signer;
	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private RecordStreamFileWriter subject;

	private MessageDigest messageDigest;
	private final static MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
}
