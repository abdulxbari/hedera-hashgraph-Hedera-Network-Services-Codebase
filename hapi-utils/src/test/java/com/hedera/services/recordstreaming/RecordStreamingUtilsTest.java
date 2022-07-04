package com.hedera.services.recordstreaming;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class RecordStreamingUtilsTest {

	private static final String PATH_TO_FILES = "src/test/resources/recordstream";

	private static final String V6_RECORD_FILE = "2022-06-14T14_49_22.456975294Z.rcd";
	private static final String V6_RECORD_SIGNATURE_FILE = "2022-06-14T14_49_22.456975294Z.rcd_sig";
	private static final String V6_SIDECAR_FILE = "2022-06-30T09_07_20.147156221Z_03.rcd";
	private static final String V5_RECORD_FILE = "V5_2022-05-27T08_27_14.157194938Z.rcd";
	private static final String V5_RECORD_SIGNATURE_FILE = "V5_2022-05-27T08_27_14.157194938Z.rcd_sig";

	@Test
	void parsingV6RecordFilesSucceeds() {
		final var recordFilePath = Path.of(PATH_TO_FILES, V6_RECORD_FILE);

		final var recordFilePair = RecordStreamingUtils.readRecordStreamFile(recordFilePath.toString());

		assertEquals(6, recordFilePair.getLeft());
		assertTrue(recordFilePair.getRight().isPresent());
	}

	@Test
	void parsingV6SignatureRecordFilesSucceeds()  {
		final var signatureFilePath = Path.of(PATH_TO_FILES, V6_RECORD_SIGNATURE_FILE);

		final var signatureFilePair = RecordStreamingUtils.readSignatureFile(signatureFilePath.toString());

		assertEquals(6, signatureFilePair.getLeft());
		assertTrue(signatureFilePair.getRight().isPresent());
	}

	@Test
	void parsingV6SidecarRecordFilesSucceeds() {
		final var sidecarFilePath = Path.of(PATH_TO_FILES, V6_SIDECAR_FILE);

		final var sidecarFileOptional = RecordStreamingUtils.readSidecarFile(sidecarFilePath.toString());

		assertTrue(sidecarFileOptional.isPresent());
	}

	@Test
	void parsingUnknownRecordFilesReturnsEmptyPair()  {
		final var recordFilePath = Path.of(PATH_TO_FILES, V5_RECORD_FILE);

		final var recordFilePair = RecordStreamingUtils.readRecordStreamFile(recordFilePath.toString());

		assertEquals(-1, recordFilePair.getLeft());
		assertFalse(recordFilePair.getRight().isPresent());
	}

	@Test
	void parsingUnknownSignatureRecordFilesReturnsEmptyPair()  {
		final var signatureFilePath = Path.of(PATH_TO_FILES, V5_RECORD_SIGNATURE_FILE);

		final var signatureFilePair = RecordStreamingUtils.readSignatureFile(signatureFilePath.toString());

		assertEquals(-1, signatureFilePair.getLeft());
		assertFalse(signatureFilePair.getRight().isPresent());
	}

	@Test
	void parsingUnknownSidecarFileReturnsEmptyOptional()  {
		final var notSidecarFilePath = Path.of(PATH_TO_FILES, V5_RECORD_SIGNATURE_FILE);

		final var sidecarOptional = RecordStreamingUtils.readSidecarFile(notSidecarFilePath.toString());

		assertTrue(sidecarOptional.isEmpty());
	}
}