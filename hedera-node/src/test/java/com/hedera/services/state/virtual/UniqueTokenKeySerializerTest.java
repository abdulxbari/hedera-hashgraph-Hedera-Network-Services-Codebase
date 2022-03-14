package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
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

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.google.common.truth.Truth.assertThat;

class UniqueTokenKeySerializerTest {
	private static final long EXAMPLE_SERIAL = 0xFAFF_FFFF_FFFF_FFFFL;

	@Test
	void deserializeToUniqueTokenKey_whenValidVersion_shouldMatch() throws IOException {
		UniqueTokenKey src = new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL);
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		src.serialize(new SerializableDataOutputStream(byteStream));

		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		ByteBuffer inputBuffer = ByteBuffer.wrap(byteStream.toByteArray());
		UniqueTokenKey dst = serializer.deserialize(inputBuffer, UniqueTokenKey.CURRENT_VERSION);
		assertThat(dst.getNum()).isEqualTo(Long.MAX_VALUE);
		assertThat(dst.getTokenSerial()).isEqualTo(EXAMPLE_SERIAL);
	}

	@Test
	void deserializeToUniqueTokenKey_whenInvalidVersion_shouldThrowException() throws IOException {
		UniqueTokenKey src = new UniqueTokenKey();
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		src.serialize(new SerializableDataOutputStream(byteStream));

		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		ByteBuffer inputBuffer = ByteBuffer.wrap(byteStream.toByteArray());
		Assertions.assertThrows(
				AssertionError.class,
				() -> serializer.deserialize(inputBuffer, UniqueTokenKey.CURRENT_VERSION + 1)
		);
	}

	@Test
	void serializeUniqueTokenKey_shouldReturnExpectedBytes() throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		int len = serializer.serialize(new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL),
				new SerializableDataOutputStream(byteStream));

		assertThat(len).isEqualTo(17);
		assertThat(byteStream.toByteArray()).isEqualTo(
				Bytes.concat(
						new byte[] { (byte) 0x88 },
						Longs.toByteArray(Long.MAX_VALUE),
						Longs.toByteArray(EXAMPLE_SERIAL))
		);
	}

	@Test
	void serializerEquals_whenCorrectDataVersion_shouldReturnTrue() throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[17]);
		new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL).serialize(buffer);
		buffer.rewind();
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();

		assertThat(
				serializer.equals(buffer,
						UniqueTokenKey.CURRENT_VERSION,
						new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL))
		).isTrue();

		buffer.rewind();
		assertThat(
				serializer.equals(buffer,
						UniqueTokenKey.CURRENT_VERSION,
						new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL)))
				.isTrue();

		buffer.rewind();
		assertThat(
				serializer.equals(buffer,
						UniqueTokenKey.CURRENT_VERSION,
						new UniqueTokenKey(10, EXAMPLE_SERIAL)))
				.isFalse();
	}

	@Test
	void serializerEquals_whenIncorrectDataVersion_shouldThrowException() throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[17]);
		new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL).serialize(buffer);
		buffer.rewind();
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		UniqueTokenKey key = new UniqueTokenKey();
		Assertions.assertThrows(
				AssertionError.class,
				() -> serializer.equals(buffer, UniqueTokenKey.CURRENT_VERSION + 1, key)
		);
	}

	@Test
	void deserializeKeySize_shouldReturnExpectedSize() throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(new byte[17]);
		new UniqueTokenKey(Long.MAX_VALUE, EXAMPLE_SERIAL).serialize(buffer);
		buffer.rewind();
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		assertThat(serializer.deserializeKeySize(buffer)).isEqualTo(17);
	}

	// Test invariants. The below tests are designed to fail if one accidentally modifies specified constants.
	@Test
	void serializer_shouldBeVariable() {
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		assertThat(serializer.getSerializedSize()).isEqualTo(DataFileCommon.VARIABLE_DATA_SIZE);
		assertThat(serializer.isVariableSize()).isTrue();
	}

	@Test
	void serializer_estimatedSize() {
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		assertThat(serializer.getTypicalSerializedSize()).isEqualTo(17);
	}

	@Test
	void serializer_version() {
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		assertThat(serializer.getVersion()).isEqualTo(1);
		assertThat(serializer.getCurrentDataVersion()).isEqualTo(1);
	}

	@Test
	void serializer_classId() {
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		assertThat(serializer.getClassId()).isEqualTo(0xb3c94b6cf62aa6c4L);
	}

	@Test
	void noopFunctions_forTestCoverage() throws IOException {
		UniqueTokenKeySerializer serializer = new UniqueTokenKeySerializer();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		SerializableDataOutputStream dataOutputStream = new SerializableDataOutputStream(outputStream);
		serializer.serialize(dataOutputStream);
		assertThat(outputStream.toByteArray()).isEmpty();

		SerializableDataInputStream dataInputStream = new SerializableDataInputStream(
				new ByteArrayInputStream(outputStream.toByteArray()));
		serializer.deserialize(dataInputStream, 1);
	}
}
