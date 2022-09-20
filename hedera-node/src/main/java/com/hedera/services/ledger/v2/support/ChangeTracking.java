package com.hedera.services.ledger.v2.support;

import com.hedera.services.ledger.v2.MutableKeyValueStore;
import com.hedera.services.ledger.v2.keys.HederaKey;

import java.time.Instant;

/**
 * A facility for tracking mappings over time in a {@link MutableKeyValueStore}.
 */
public interface ChangeTracking {
    /**
     * Records the given key's mapping as having changed at some time.
     *
     * @param key the key of the changed mapping
     * @param at the time it changed
     */
    void trackMappingChange(HederaKey key, Instant at);

    /**
     * Invites the change-tracking facility to "forget" any changes old enough
     * to stop tracking.
     *
     * @param now the "current" time
     */
    void forgetOldChanges(Instant now);

    /**
     * Returns whether the mapping for the given key has changed since the given time.
     * <i>May</i> return false positives (for example, if the tracking facility has "forgotten"
     * change information around the given time). <i>Must not</i> return false negatives.
     *
     * @param then a time of interest
     * @return false if the mapping is known to be unchanged since the given time, true otherwise
     */
    boolean hasChangedSince(HederaKey key, Instant then);
}
