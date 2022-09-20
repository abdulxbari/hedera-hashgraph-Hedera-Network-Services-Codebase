package com.hedera.services.ledger.v2;

import com.hedera.services.ledger.v2.frame.ReversibleChange;
import com.hedera.services.ledger.v2.keys.HederaKey;

import java.util.function.Consumer;

/**
 * A {@link MutableKeyValueStore} that permits buffering a set of key-value
 * mappings into a "frame" before either committing or reverting them all
 * atomically. It also supports reviewing all changes from a specified list
 * of key types.
 *
 * <p><b>IMPORTANT:</b> it is possible to call {@code beginFrame()} several
 * times, creating a "stack" of frames, where all changes are buffered in the
 * "topmost" frame until it is committed or reverted; at which point changes
 * start buffering in the new topmost frame.
 */
public interface TransactionalKeyValueStore extends MutableKeyValueStore {
    /**
     * Begins a new frame of changes that should be committed or reverted atomically.
     */
    void beginFrame();

    /**
     * Reverts all changes accumulated since the last call to {@code beginFrame()}.
     *
     * @throws IllegalStateException if no frame has begun
     */
    void revertFrame();

    /**
     * Commits all changes accumulated since the last call to {@code beginFrame()}.
     * If the current frame is the only frame, a "commit" means persisting new key/value
     * mappings to the store represented by this object. Otherwise, a "commit" simply
     * squashes the change sin the current frame to the frame below it.
     *
     * @throws IllegalStateException if no frame has begun
     */
    void commitFrame();

    /**
     * Exposes all pending changes of the requested key types in the current frame to the given observer.
     *
     * <p><b>IMPORTANT:</b> The observer will be called at most once for each distinct key.
     *
     * @param observer a callback to receive information about pending changes
     * @param keyTypes the key types of interest
     */
    void reviewChangesInFrame(Consumer<ReversibleChange<?>> observer, Class<? extends HederaKey>... keyTypes);
}
