/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.recordstreaming;

import com.google.common.primitives.Ints;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.SignatureFile;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.apache.commons.lang3.tuple.Pair;

/** Minimal utility to read record stream files and their corresponding signature files. */
public class RecordStreamingUtils {
    private RecordStreamingUtils() {}

    public static Pair<Integer, Optional<RecordStreamFile>> readUncompressedRecordStreamFile(
            final String fileLoc) throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            final var recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
            final var recordStreamFile = RecordStreamFile.parseFrom(fin);
            return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
        }
    }

    public static Pair<Integer, Optional<RecordStreamFile>> readRecordStreamFile(
            final String fileLoc) throws IOException {
        final var uncompressedFileContents = getUncompressedStreamFileBytes(fileLoc);
        final var recordFileVersion = ByteBuffer.wrap(uncompressedFileContents, 0, 4).getInt();
        final var recordStreamFile =
                RecordStreamFile.parseFrom(
                        ByteBuffer.wrap(
                                uncompressedFileContents, 4, uncompressedFileContents.length - 4));
        return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
    }

    public static Pair<Integer, Optional<SignatureFile>> readSignatureFile(final String fileLoc)
            throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            final var recordFileVersion = fin.read();
            final var recordStreamSignatureFile = SignatureFile.parseFrom(fin);
            return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamSignatureFile));
        }
    }

    public static SidecarFile readUncompressedSidecarFile(final String fileLoc) throws IOException {
        try (final var fin = new FileInputStream(fileLoc)) {
            return SidecarFile.parseFrom(fin);
        }
    }

    public static SidecarFile readSidecarFile(final String fileLoc) throws IOException {
        return SidecarFile.parseFrom(getUncompressedStreamFileBytes(fileLoc));
    }

    public static byte[] getUncompressedStreamFileBytes(final String fileLoc) throws IOException {
        try (final var fin = new GZIPInputStream(new FileInputStream(fileLoc));
                final var byteArrayOutputStream = new ByteArrayOutputStream()) {
            final var buffer = new byte[1024];
            int len;
            while ((len = fin.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, len);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

    public static Hash computeFileHashFrom(
            final Integer version, final RecordStreamFile recordStreamFile) {
        try {
            final var messageDigest =
                    MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
            messageDigest.update(Ints.toByteArray(version));
            messageDigest.update(recordStreamFile.toByteArray());
            return new Hash(messageDigest.digest(), Cryptography.DEFAULT_DIGEST_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    public static Hash computeMetadataHashFrom(
            final Integer version, final RecordStreamFile recordStreamFile) {
        final MessageDigest messageDigest;
        try {
            messageDigest =
                    MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        try (final var outputStream =
                new SerializableDataOutputStream(new HashingOutputStream(messageDigest)); ) {
            // digest file header
            outputStream.writeInt(version);
            final var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
            outputStream.writeInt(hapiProtoVersion.getMajor());
            outputStream.writeInt(hapiProtoVersion.getMinor());
            outputStream.writeInt(hapiProtoVersion.getPatch());
            // digest startRunningHash
            outputStream.write(
                    recordStreamFile.getStartObjectRunningHash().getHash().toByteArray());
            // digest endRunningHash
            outputStream.write(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray());
            // digest block number
            outputStream.writeLong(recordStreamFile.getBlockNumber());
            final var digest = messageDigest.digest();
            return new Hash(digest, Cryptography.DEFAULT_DIGEST_TYPE);
        } catch (Exception e) {
            return new Hash("error".getBytes(StandardCharsets.UTF_8));
        }
    }
}
