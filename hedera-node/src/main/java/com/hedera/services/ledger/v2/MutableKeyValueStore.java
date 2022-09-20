package com.hedera.services.ledger.v2;

import com.hedera.services.ledger.v2.keys.*;
import com.hedera.services.ledger.v2.support.ChangeTracking;

/**
 * A data source that permits both reading and writing {@link HederaKey}-value mappings.
 *
 * <p><b>IMPORTANT:</b> null object values are not allowed.
 */
public interface MutableKeyValueStore extends ReadOnlyKeyValueStore {
    /**
     * Either creates or updates an object-valued mapping in the store.
     *
     * <p>Because a key-value mapping can represent either an entity property
     * (e.g., an account's balance) or <i>an entire entity</i> (e.g., an account),
     * the semantics for <tt>put()</tt> include both creating entities and
     * setting their properties. So the primitive specializations below are
     * exclusively for setting properties.
     *
     * @param key the key for a mapping of interest
     * @param value the desired mapped value
     * @param <V> the type of the value
     * @throws NullPointerException if the value is null
     */
    <V> void put(HederaObjectKey<V> key, V value);

    /**
     * Either creates or updates an int-valued mapping in the store.
     *
     * @param key the key for a mapping of interest
     * @param value the desired mapped value
     */
    void putInt(HederaIntKey key, int value);

    /**
     * Either creates or updates a long-valued mapping in the store.
     *
     * @param key the key for a mapping of interest
     * @param value the desired mapped value
     */
    void putLong(HederaLongKey key, long value);

    /**
     * Either creates or updates a boolean-valued mapping in the store.
     *
     * @param key the key for a mapping of interest
     * @param value the desired mapped value
     */
    void putBoolean(HederaBooleanKey key, boolean value);

    /**
     * Removes a key/value mapping from the store.
     *
     * <p>Because primitive values are exclusively properties of entities, and it
     * does not make sense to remove a single property of an entity, the semantics
     * for <tt>remove()</tt> only apply to entities. For the same reason, there are
     * no primitive specializations of this method (e.g., <tt>removeLong()</tt>).
     *
     * @param key the key of the mapping to remove
     */
    void remove(HederaObjectKey<?> key);

    /**
     * If supported, begins tracking all changes to this key-value store with
     * the provided {@link ChangeTracking} implementation.
     *
     * @param changeTracking the change tracking facility to use
     */
    default void trackChangesWith(final ChangeTracking changeTracking) {
        throw new UnsupportedOperationException();
    }
}
