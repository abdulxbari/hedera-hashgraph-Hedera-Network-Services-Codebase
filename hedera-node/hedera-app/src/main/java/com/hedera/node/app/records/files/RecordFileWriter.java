package com.hedera.node.app.records.files;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.SidecarMetadata;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static com.hedera.hapi.streams.schema.RecordStreamFileSchema.*;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeLong;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;

/**
 * An incremental record file writer that writes a single RecordStreamItem at a time. It also maintains
 * a hash as it is going that can be fetched at the end after closing. A single RecordFileWriter
 * represents one record file and has a life-span of writing a single file. This is a abstract base class that can
 * be extended to implement different versions of the record file format.
 * <p>
 *      Also, responsible for computing running hashes, this is not done inline so that it can be
 *      done in background thread if desired.
 * </p><p>
 *     All methods are expected to be called on a single thread other than those specified.
 * </p>
 */
public final class RecordFileWriter implements AutoCloseable {
    /** The file we are writing to */
    private final Path file;
    /** The file output stream we are writing to */
    private final FileOutputStream fileOutputStream;
    /** The gzip output stream we are writing to, wraps {@code fileOutputStream} */
    private final GZIPOutputStream gzipOutputStream;
    /** HashingOutputStream for hashing the file contents, wraps {@code gzipOutputStream} or {@code fileOutputStream} */
    private final HashingOutputStream hashingOutputStream;
    /** The buffered output stream we are writing to, wraps {@code hashingOutputStream} */
    private final BufferedOutputStream bufferedOutputStream;
    /** WritableStreamingData we are writing to, wraps {@code bufferedOutputStream} */
    private final WritableStreamingData outputStream;
    /** The format of the record file we are writing */
    private final RecordFileFormat recordFileFormat;

    /** The starting running hash before any items in this file */
    private HashObject startObjectRunningHash;
    /** The end running hash, stored for queries by StreamFileProducer */
    private HashObject endObjectRunningHash;
    /** The hash of the file contents, computed in close */
    private Bytes hash = null;

    /**
     * Creates a new incremental record file writer on a new file. Writing the header fields of
     *
     * @param file The path to the record file to create and write
     * @param recordFileFormat The format of the record file to write
     * @param compressFile true if the file should be gzip compressed
     */
    public RecordFileWriter(@NonNull final Path file,
                            @NonNull final RecordFileFormat recordFileFormat,
                            final boolean compressFile) {
        try {
            this.file = file;
            this.recordFileFormat = recordFileFormat;
            // create parent directories if needed
            Files.createDirectories(file.getParent());
            // create digest for hashing the file contents
            MessageDigest wholeFileDigest;
            try {
                wholeFileDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            // create parent directories if needed
            Files.createDirectories(file.getParent());
            // create stream chain
            fileOutputStream = new FileOutputStream(file.toFile());
            if(compressFile) {
                gzipOutputStream = new GZIPOutputStream(fileOutputStream);
                hashingOutputStream = new HashingOutputStream(wholeFileDigest,gzipOutputStream);
            } else {
                gzipOutputStream = null;
                hashingOutputStream = new HashingOutputStream(wholeFileDigest,fileOutputStream);
            }
            bufferedOutputStream = new BufferedOutputStream(hashingOutputStream);
            outputStream = new WritableStreamingData(bufferedOutputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Closes this resource
     */
    @Override
    public void close() throws Exception {
        // There are a lot of flushes and closes here, but unfortunately it is not guaranteed that a OutputStream will
        // propagate though a chain of streams. So we have to flush and close each one individually.
        bufferedOutputStream.flush();
        if(gzipOutputStream != null) gzipOutputStream.flush();
        fileOutputStream.flush();
        outputStream.close();
        bufferedOutputStream.close();
        if(gzipOutputStream != null) gzipOutputStream.close();
        fileOutputStream.close();
        hash = Bytes.wrap(hashingOutputStream.getDigest());
    }

    // ================================================================================================================================
    // Getters used by StreamFileProducer

    /**
     * Get the hash for the written file, will return null before file is finished writing and has been closed.
     *
     * @return the hash of file bytes. This is the hash of the uncompressed file bytes.
     */
    public Bytes uncompressedFileHash() {
        return hash;
    }

    /**
     * Get the path to the file being written
     *
     * @return the path to the file
     */
    public Path filePath() {
        return file;
    }

    /**
     * Get the starting running hash
     *
     * @return the starting running hash
     */
    public HashObject startObjectRunningHash() {
        return startObjectRunningHash;
    }

    /**
     * Get the ending running hash
     *
     * @return the ending running hash
     */
    public HashObject endObjectRunningHash() {
        return endObjectRunningHash;
    }

    /**
     * Write header to file, the header must include at least the first 4 byte integer version
     *
     * @param hapiProtoVersion the HAPI version of protobuf
     * @param startObjectRunningHash the starting running hash at the end of previous record file
     */
    public void writeHeader(@NonNull final SemanticVersion hapiProtoVersion,
                     @NonNull final HashObject startObjectRunningHash) {
        this.startObjectRunningHash = startObjectRunningHash;
        recordFileFormat.writeHeader(outputStream, hapiProtoVersion, startObjectRunningHash);
    }

    /**
     * Write a serialized record stream item of type T.
     *
     * @param item the item to extract/convert
     */
    public void writeRecordStreamItem(@NonNull final SerializedSingleTransactionRecord item) {
        recordFileFormat.writeRecordStreamItem(outputStream, item);
    }

    /**
     * Write the footer to the file
     *
     * @param endRunningHash the ending running hash after the last record stream item
     * @param blockNumber the block number of this file
     * @param sidecarMetadata The sidecar metadata to write
     */
    public void writeFooter(@NonNull final HashObject endRunningHash,
                             final long blockNumber,
                             @NonNull final List<SidecarMetadata> sidecarMetadata) {
        this.endObjectRunningHash = endRunningHash;
        recordFileFormat.writeFooter(outputStream, endRunningHash, blockNumber, sidecarMetadata);
    }
}
