package com.hedera.services.ledger.v2;

import com.hedera.services.ledger.v2.frame.ReversibleChange;

import java.util.function.Consumer;

/**
 * A {@link MutableKeyValueStore} that permits buffering a set of key-value
 * mappings into a "frame" before either committing or reverting them all
 * atomically.
 *
 * <p><b>IMPORTANT:</b> it is possible to call {@code beginFrame()} several
 * times, creating a "stack" of frames, where all changes are buffered in the
 * "topmost" frame until it is committed or reverted; at which point changes
 * start buffering in the new topmost frame.
 */
public interface TransactionalKeyValueStore extends MutableKeyValueStore {
    void beginFrame();

    void revertFrame();

    void commitFrame();

    void reviewChangesInFrame(Consumer<ReversibleChange> observer);
}
