package com.hedera.node.app.record;

import com.hedera.node.app.state.HederaState;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RecordStreamManager {
    // TODO I need the HAPI Version
    // TODO I need the object running hash. This comes from state, and I need to update state
    // TODO block number? What the heck is this?
    // TODO what about sidecars?

    private final Path dir;
    private final RecordStreamFile.Builder recordStreamFileBuilder;
    private Instant lastConsensusTime = Instant.MIN;

    private final LinkedBlockingQueue<RecordStreamFile> filesToWrite = new LinkedBlockingQueue<>(10); // TODO get from config...

    public RecordStreamManager(@NonNull final HederaState hederaState, @NonNull final Path dir, @NonNull final Executor exec) {
        this.dir = Objects.requireNonNull(dir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("The path '" + dir + "' is not a directory");
        }

        final var states = hederaState.createReadableStates("RecordStreamManager");
        final var state = states.get("Data");

        recordStreamFileBuilder = RecordStreamFile.newBuilder()
                .setHapiProtoVersion(null) // TODO
                .setStartObjectRunningHash(null) // TODO same as the end hash of the last file
                .setBlockNumber(-1L); // TODO I get this from state?


        // Start a long-lived thread that will only terminate when interrupted (which
        // happens if the executor is shutdown).
        exec.execute(() -> {
            while (!Thread.interrupted()) {
                try {
                    final var toWrite = filesToWrite.take();
                    final var recordPath = dir.resolve("record file name goes here");
                    while (true) {
                        try {
                            Files.write(recordPath, toWrite.toByteArray(), StandardOpenOption.CREATE);
                            break;
                        } catch (IOException e) {
                            // TODO LOG that we failed to write a record path.
                            System.err.println("FAILED to write a record file! Will retry in 1 second");
                            TimeUnit.SECONDS.sleep(1);
                        } catch (Throwable th) {
                            // TODO Something horrible happened, we need to fail fast.
                            System.err.println("SOMETHING REALLY BAD HAPPENED!");
                            th.printStackTrace();
                            throw th;
                        }
                    }
                } catch (InterruptedException ignore) {
                    // TODO Is this true?
                    // Any interrupted exception means the thread should terminate.
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    void submit(@NonNull final Transaction tx, @NonNull final TransactionRecord record) {
        final var consensusTime = Instant.ofEpochSecond(
                record.getConsensusTimestamp().getSeconds(),
                record.getConsensusTimestamp().getNanos());

        onConsensusTimeAdvanced(consensusTime);

        recordStreamFileBuilder.addRecordStreamItems(RecordStreamItem.newBuilder()
                        .setTransaction(tx)
                        .setRecord(record));
    }

    public void onConsensusTimeAdvanced(Instant consensusTime) {
        if (!consensusTime.isAfter(lastConsensusTime)) {
            // TODO ERROR! The consensus time must be strictly increasing!!
        }

        // TODO If it has been 2 seconds (where 2 is supposed to be configurable!!!!
        // TODO what about exactly 2 seconds delta?
        if (consensusTime.isAfter(lastConsensusTime.plus(2, ChronoUnit.SECONDS))) {
            recordStreamFileBuilder.setEndObjectRunningHash(null); // TODO

            final var recordStreamFile = recordStreamFileBuilder.build();
            recordStreamFileBuilder.clear()
                    .setHapiProtoVersion(null) // TODO
                    .setStartObjectRunningHash(null) // TODO same as the end hash of the last file
                    .setBlockNumber(-1L); // TODO I get this from state?

            if (recordStreamFile.getRecordStreamItemsCount() > 0) {
                filesToWrite.offer(recordStreamFileBuilder.build()); // TODO Potentially blocking maybe use timeout version and log statements!!!
            }
        }
    }
}
