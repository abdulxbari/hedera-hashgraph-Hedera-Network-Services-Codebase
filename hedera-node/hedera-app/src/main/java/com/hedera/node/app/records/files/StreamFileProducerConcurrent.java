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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.stream.Signer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Implementation of StreamFileProducerBase
 *
 * <p>
 *     Threading model: is really important here, This class depends on the fact that public methods are all called on
 *     handle transaction thread. So are single threaded and executed in the correct order. The information provided by
 *     calls to public methods is then processed into files by the background threads. All async task done with
 *     CompletableFutures should NEVER use non-final variables(local or class), as they may change unexpectedly between
 *     the time the future is created and the time it is executed.
 * </p>
 * <p>
 *     We have two flows happening while data is arriving for a block record file. The first is maintaining a running
 *     hash of record stream items as they arrive providing a future
 * </p>
 */
@SuppressWarnings({"CodeBlock2Expr"})
public final class StreamFileProducerConcurrent extends StreamFileProducerBase {
    /** Simple pair class */
    record TwoResults<A, B>(A a, B b) {}

    /** The hapi version of the node */
    private final SemanticVersion hapiVersion;
    /** The executor service to use for background tasks */
    private final ExecutorService executorService;
    /** Future for running hash results of last running hash updates task */
    private CompletableFuture<Bytes> lastRecordHashingResult = null;
    /** Future for the current record file writer, this will always get you the record file writer for teh current file being written */
    private CompletableFuture<RecordFileWriter> currentRecordFileWriter = null;
    /** Future to get list of current sidecar file writers, starts with completed empty list to save handing null at start */
    private CompletableFuture<List<SidecarFileWriter>> sidecarHandlingFuture = CompletableFuture.completedFuture(null);

    /**
     * Construct StreamFileProducerConcurrent
     *
     * @param configProvider the configuration to read from
     * @param nodeInfo the current node information
     * @param signer the signer to use for signing in signature files
     * @param fileSystem the file system to use, needed for testing to be able to use a non-standard file
     *                   system. If null default is used.
     * @param executorService The executor service to use for background threads
     */
    public StreamFileProducerConcurrent(
            @NonNull final ConfigProvider configProvider,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final Signer signer,
            @Nullable final FileSystem fileSystem,
            @NonNull final ExecutorService executorService) {
        super(configProvider, nodeInfo, signer, fileSystem);
        this.executorService = executorService;
        this.hapiVersion = nodeInfo.hapiVersion();
    }

    /**
     * Set the current running hash of record stream items. This is called only once to initialize the running hash on startup.
     * This is called on handle transaction thread.
     *
     * @param recordStreamItemRunningHash The new running hash, all future running hashes produced will flow from this
     */
    @Override
    public void setRunningHash(Bytes recordStreamItemRunningHash) {
        lastRecordHashingResult = CompletableFuture.completedFuture(recordStreamItemRunningHash);
    }

    /**
     * Get the current running hash of record stream items. This is called on handle transaction thread. It will block if background thread is still hashing.
     *
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @Override
    public Bytes getCurrentRunningHash() {
        return lastRecordHashingResult == null ? null : lastRecordHashingResult.join();
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
    public void switchBlocks(
            final long lastBlockNumber,
            final long newBlockNumber,
            @NonNull final Instant newBlockFirstTransactionConsensusTime) {
        System.out.println("StreamFileProducerConcurrent.switchBlocks lastBlockNumber=" + lastBlockNumber + " newBlockNumber=" + newBlockNumber + " newBlockFirstTransactionConsensusTime=" + newBlockFirstTransactionConsensusTime);
        if (lastRecordHashingResult == null) {
            throw new RuntimeException("setRunningHash() must be called before switchBlocks()");
        }
        if (currentRecordFileWriter == null) {
            // we are at the start of a new block and there is no old one to close or wait for. So just create a new one
            // which creates a
            // new file and writes header in background.
            currentRecordFileWriter = lastRecordHashingResult.thenApply(lastRunningHash -> {
                return createRecordFileWriter(newBlockFirstTransactionConsensusTime, lastRunningHash, newBlockNumber);
            });
        } else {
            // it is important to capture references to these variables as they are at this point in time synchronously
            // because we will use them in the asynchronous background task and by the time they are used there they
            // could have changed. Even more they could have been changed by us, so we can end up joining on ourselves
            // and deadlocking. This is important for the use of allOf() as it does not pass the results down.
            final var lastRecordHashingResultLOCAL = lastRecordHashingResult;
            final var currentRecordFileWriterLOCAL = currentRecordFileWriter;
            final var sidecarHandlingFutureLOCAL = sidecarHandlingFuture;
            // we need to set sidecarHandlingFuture to null as we are about to close the block and the last open sidecar
            // new writing will need to start a new set of sidecars.
            sidecarHandlingFuture = CompletableFuture.completedFuture(null);
            // wait for all background threads to finish, then in new background task finish the current block

//            System.out.println("****** switchBlocks DEPENDS ON "+currentRecordFileWriterLOCAL);
            currentRecordFileWriter = CompletableFuture.allOf(
                            lastRecordHashingResultLOCAL, currentRecordFileWriterLOCAL, sidecarHandlingFutureLOCAL)
                    .thenApplyAsync(
                            aVoid -> {
                                final Bytes lastRunningHash = lastRecordHashingResultLOCAL.join();
                                final RecordFileWriter recordFileWriter = currentRecordFileWriterLOCAL.join();
                                final List<SidecarFileWriter> sidecarFileWriters = sidecarHandlingFutureLOCAL.join();
                                // close current block
                                closeBlock(
                                        recordFileWriter
                                                .startObjectRunningHash()
                                                .hash(),
                                        lastRunningHash,
                                        recordFileWriter,
                                        sidecarFileWriters);
                                // create new record file writer
                                return createRecordFileWriter(
                                        newBlockFirstTransactionConsensusTime, lastRunningHash, newBlockNumber);
                            },
                            executorService);
//            System.out.println("****** switchBlocks PRODUCED "+currentRecordFileWriter);
        }
    }

    /**
     * Create a new record stream writer for the correct record format version and write the file header.
     * <p>
     *     Called on background threads so should not access any non-final state.
     * </p>
     *
     * @param newBlockFirstTransactionConsensusTime the consensus time of the first transaction in the block
     * @param lastRunningHash the last running hash of the previous block to write into file header
     * @return the new record stream writer
     */
    private RecordFileWriter createRecordFileWriter(
            @NonNull final Instant newBlockFirstTransactionConsensusTime,
            @NonNull final Bytes lastRunningHash,
            final long blockNumber) {
        final var recordFileWriter = new RecordFileWriter(
                getRecordFilePath(newBlockFirstTransactionConsensusTime),
                recordFileFormat,
                compressFilesOnCreation,
                blockNumber);
        recordFileWriter.writeHeader(
                hapiVersion, new HashObject(HashAlgorithm.SHA_384, (int) lastRunningHash.length(), lastRunningHash));
        return recordFileWriter;
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
            @NonNull final Instant blockFirstTransactionConsensusTime,
            @NonNull final Stream<SingleTransactionRecord> recordStreamItems) {
        if (lastRecordHashingResult == null) {
            throw new RuntimeException("setRunningHash() must be called before writeRecordStreamItems()");
        }
        // serialize all the record stream items in background thread into SerializedSingleTransaction objects
        CompletableFuture<List<SerializedSingleTransactionRecord>> futureSerializedRecords =
                CompletableFuture.supplyAsync(
                        () -> {
                            return recordStreamItems
                                    .map(item -> recordFileFormat.serialize(item, blockNumber, hapiVersion))
                                    .toList();
                        },
                        executorService);
        // when serialization is done and previous running hash is computed, we can compute new running hash and write
        // serialized
        // items to record file in parallel update running hash in a background thread
        lastRecordHashingResult = lastRecordHashingResult
                .thenCombine(futureSerializedRecords, TwoResults::new)
                .thenApplyAsync(
                        twoResults -> {
                            final Bytes lastRecordHashingResult = twoResults.a();
                            final List<SerializedSingleTransactionRecord> serializedItems = twoResults.b();
                            return recordFileFormat.computeNewRunningHash(lastRecordHashingResult, serializedItems);
                        },
                        executorService);
        // write serialized items to record file in a background thread
//        System.out.println("****** writeRecordStreamItems DEPENDS ON "+currentRecordFileWriter);
        currentRecordFileWriter = currentRecordFileWriter
                .thenCombine(futureSerializedRecords, TwoResults::new)
                .thenApplyAsync(
                        twoResults -> {
                            final RecordFileWriter recordFileWriter = twoResults.a();
                            final List<SerializedSingleTransactionRecord> serializedItems = twoResults.b();
                            serializedItems.forEach(recordFileWriter::writeRecordStreamItem);
                            return recordFileWriter;
                        },
                        executorService);
//        System.out.println("****** writeRecordStreamItems PRODUCED "+currentRecordFileWriter);
        // write serialized sidecar items to sidecar files in a background thread
//        System.out.println("****** sidecarHandlingFuture DEPENDS ON "+sidecarHandlingFuture);
        sidecarHandlingFuture = sidecarHandlingFuture
                .thenCombine(futureSerializedRecords, TwoResults::new)
                .thenApplyAsync(
                        twoResults -> {
                            final List<SidecarFileWriter> sidecarFileWriters = twoResults.a();
                            final List<SerializedSingleTransactionRecord> serializedItems = twoResults.b();
                            return handleSidecarItems(
                                    blockFirstTransactionConsensusTime, sidecarFileWriters, serializedItems);
                        },
                        executorService);
//        System.out.println("****** sidecarHandlingFuture PRODUCED "+sidecarHandlingFuture);
    }

    /**
     * Closes this StreamFileProducerBase wait for any background thread, close all files etc. This method is
     * synchronous, so waits for all background threads to finish and files to be closed.
     */
    @Override
    public void close() throws Exception {
        // it is important to capture references to these variables as they are at this point in time synchronously
        // because we will use them in the asynchronous background task and by the time they are used there they
        // could have changed. Even more they could have been changed by us, so we can end up joining on ourselves
        // and deadlocking. This is important for the use of allOf() as it does not pass the results down.
        final var lastRecordHashingResultLOCAL = lastRecordHashingResult;
        final var currentRecordFileWriterLOCAL = currentRecordFileWriter;
        final var sidecarHandlingFutureLOCAL = sidecarHandlingFuture;
        // set currentRecordFileWriter to null as we are closing it
        currentRecordFileWriter = null;
        // wait for all background threads to finish, then in new background task finish the current block
//        System.out.println("****** close DEPENDS ON Writers  :: "+currentRecordFileWriterLOCAL);
//        System.out.println("****** close DEPENDS ON SideCars :: "+sidecarHandlingFutureLOCAL);
        CompletableFuture.allOf(lastRecordHashingResultLOCAL, currentRecordFileWriterLOCAL, sidecarHandlingFutureLOCAL)
                .thenAccept(aVoid -> {
                    final Bytes lastRunningHash = lastRecordHashingResultLOCAL.join();
                    final RecordFileWriter recordFileWriter = currentRecordFileWriterLOCAL.join();
                    final List<SidecarFileWriter> sidecarFileWriters = sidecarHandlingFutureLOCAL.join();
                    System.out.println("lastRecordHashingResultLOCAL.isDone() = " + lastRecordHashingResultLOCAL.isDone());
                    System.out.println("currentRecordFileWriterLOCAL.isDone() = " + currentRecordFileWriterLOCAL.isDone());
                    System.out.println("sidecarHandlingFutureLOCAL["+sidecarHandlingFutureLOCAL+"].isDone() = " + sidecarHandlingFutureLOCAL.isDone());
                    System.out.println("sidecarFileWriters.size() = " + sidecarFileWriters.size());
                    // close current block
                    closeBlock(
                            recordFileWriter.startObjectRunningHash().hash(),
                            lastRunningHash,
                            recordFileWriter,
                            sidecarFileWriters);
                })
                .join(); // join() is needed to wait for allOf() to finish
    }
}
