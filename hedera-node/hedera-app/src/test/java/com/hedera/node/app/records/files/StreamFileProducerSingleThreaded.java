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

import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.FileSystem;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * A single threaded implementation of StreamFileProducerBase
 * <p>
 *     This is not intended to be used in production, but is can be used in tests and provides a easy to understand
 *     implementation of StreamFileProducerBase
 * </p>
 */
public final class StreamFileProducerSingleThreaded extends StreamFileProducerBase {
    /** The current running hash */
    private Bytes currentRunningHash = null;
    /** The consensus time of the first transaction in the current record file */
    private Instant currentRecordFileWriterFirstTransactionConsensusTime = null;
    /** The current record file writer */
    private RecordFileWriter currentRecordFileWriter = null;
    /** List of current sidecar file writers */
    private List<SidecarFileWriter> sidecarFileWriters = null;

    /**
     * Construct RecordManager and start background thread
     *
     * @param configProvider the configuration to read from
     * @param nodeInfo the current node information
     * @param signer the signer to use for signing in signature files
     * @param fileSystem the file system to use, needed for testing to be able to use a non-standard file
     *                   system. If null default is used.
     */
    public StreamFileProducerSingleThreaded(
            @NonNull final ConfigProvider configProvider,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final Signer signer,
            @Nullable final FileSystem fileSystem) {
        super(configProvider, nodeInfo, signer, fileSystem);
    }

    // =========================================================================================================================================================================
    // public methods

    /**
     * Closes this StreamFileProducerBase wait for any background thread, close all files etc.
     */
    @Override
    public void close() {
        if (currentRecordFileWriter != null) {
            closeBlock(
                    currentRecordFileWriter.startObjectRunningHash().hash(),
                    currentRunningHash,
                    currentRecordFileWriter,
                    sidecarFileWriters);
            // start a new set of file writers for new block
            sidecarFileWriters = null;
        }
    }

    /**
     * Set the current running hash of record stream items. This is called only once to initialize the running hash on startup.
     * This is called on handle transaction thread.
     *
     * @param recordStreamItemRunningHash The new running hash, all future running hashes produced will flow from this
     */
    @Override
    public void setRunningHash(Bytes recordStreamItemRunningHash) {
        currentRunningHash = recordStreamItemRunningHash;
    }

    /**
     * Get the current running hash of record stream items. This is called on handle transaction thread. It will block if background thread is still hashing.
     *
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @Override
    public Bytes getCurrentRunningHash() {
        return currentRunningHash;
    }

    /**
     * Called at the end of a block and start of next block.
     * <p>If there is a currently open block it closes any open stream files and writes signature file.</p>
     * <p>Then opens a new record file for the new block</p>
     *
     * @param lastBlockNumber                       The number for the block we are closing. If lastBlockNumber is <=0 then
     *                                              there is no block to close.
     * @param newBlockNumber                        The number for the block we are opening.
     * @param newBlockFirstTransactionConsensusTime The consensus time of the first transaction in the new block. It must be the
     *                                              adjusted consensus time not the platform assigned consensus time. Assuming
     *                                              the two are different.
     */
    @Override
    public void switchBlocks(long lastBlockNumber, long newBlockNumber, Instant newBlockFirstTransactionConsensusTime) {
        if (currentRecordFileWriter != null) {
            closeBlock(
                    currentRecordFileWriter.startObjectRunningHash().hash(),
                    currentRunningHash,
                    currentRecordFileWriter,
                    sidecarFileWriters);
            // start a new set of file writers for new block
            sidecarFileWriters = null;
        }
        // open new block record file and write header
        currentRecordFileWriterFirstTransactionConsensusTime = newBlockFirstTransactionConsensusTime;
        currentRecordFileWriter = new RecordFileWriter(
                getRecordFilePath(newBlockFirstTransactionConsensusTime),
                recordFileFormat,
                compressFilesOnCreation,
                newBlockNumber);
        currentRecordFileWriter.writeHeader(
                hapiVersion,
                new HashObject(HashAlgorithm.SHA_384, (int) currentRunningHash.length(), currentRunningHash));
    }

    /**
     * Write record items to stream files. They must be in exact consensus time order! This must only be called after the user
     * transaction has been committed to state and is 100% done.
     *
     * @param blockNumber the block number for this block that we are writing record stream items for
     * @param blockFirstTransactionConsensusTime the consensus time of the first transaction in the block
     * @param recordStreamItems the record stream items to write
     */
    @Override
    public void writeRecordStreamItems(
            final long blockNumber,
            @NonNull Instant blockFirstTransactionConsensusTime,
            @NonNull final Stream<SingleTransactionRecord> recordStreamItems) {
        // Serialize record stream items
        List<SerializedSingleTransactionRecord> serializedItems = recordStreamItems
                .map(item -> recordFileFormat.serialize(item, blockNumber, hapiVersion))
                .toList();
        // compute new running hash, by adding each serialized record stream item to the current running hash
        currentRunningHash = recordFileFormat.computeNewRunningHash(currentRunningHash, serializedItems);
        // write serialized items to record file
        serializedItems.forEach(item -> currentRecordFileWriter.writeRecordStreamItem(item));
        // handle sidecar items
        sidecarFileWriters = handleSidecarItems(
                currentRecordFileWriterFirstTransactionConsensusTime, sidecarFileWriters, serializedItems);
    }
}
