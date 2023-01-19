package com.hedera.node.app.spi.record;

import java.util.LinkedList;

/**
 * An ordered set of {@link RecordStreamObject}s to be committed as a single logical group for record
 * processing. Individual {@link RecordStreamObject}s in this set may be bundled up and included in
 * the same, or different, record stream files, depending on their consensus timestamp. In the case
 * of child transactions, they will always be included in the same record stream file as their
 * parent transaction.
 */
public class RecordSet {
    private LinkedList<RecordStreamObject> preceding = new LinkedList<>();
    private RecordStreamObject primary;
    private LinkedList<RecordStreamObject> following = new LinkedList<>();

    // --- Methods for adding a new preceding item, or following item. Verification that
    //     following items are always children of the primary (ideally that wouldn't even
    //     be exposed to any service code...). Primary object must be non-null. This object
    //     is created by the RecordBuilders.... maybe it should be a Java record?
}
