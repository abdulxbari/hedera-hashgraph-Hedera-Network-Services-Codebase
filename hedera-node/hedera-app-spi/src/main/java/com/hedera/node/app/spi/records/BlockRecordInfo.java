package com.hedera.node.app.spi.records;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.time.Instant;

/**
 * Interface for reading running hashes and block information managed by BlockRecordManager
 */
public interface BlockRecordInfo {

    // ========================================================================================================
    // Running Hash Methods

    /**
     * Get the runningHash of all RecordStreamObject. This will block if the running hash has not yet
     * been computed for the most recent user transaction.
     *
     * @return the runningHash of all RecordStreamObject
     */
    Bytes getRunningHash();

    /**
     * Get the previous, previous, previous runningHash of all RecordStreamObject. This will block if
     * the running hash has not yet been computed for the most recent user transaction.
     *
     * @return the previous, previous, previous runningHash of all RecordStreamObject
     */
    @Nullable Bytes getNMinus3RunningHash();

    // ========================================================================================================
    // Block Methods

    /**
     * Get the last block number, this is the last completed immutable block.
     *
     * @return the current block number
     */
    long lastBlockNo();

    /**
     * Get the consensus time of the first transaction of the last block, this is the last completed immutable block.
     *
     * @return the consensus time of the first transaction of the last block
     */
    @Nullable Instant firstConsTimeOfLastBlock();

    /**
     * Gets the hash of the last block
     *
     * @return the last block hash, null if no blocks have been created
     */
    @Nullable Bytes lastBlockHash();


    /**
     * Returns the hash of the given block number, or {@code null} if unavailable.
     *
     * @param blockNo the block number of interest, must be within range of (current_block - 1) -> (current_block - 254)
     * @return its hash, if available otherwise null
     */
    @Nullable Bytes blockHashByBlockNumber(final long blockNo);
}
