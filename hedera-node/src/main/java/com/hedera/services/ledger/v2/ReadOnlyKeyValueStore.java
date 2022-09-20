package com.hedera.services.ledger.v2;

import com.hedera.services.ledger.v2.keys.*;

import javax.annotation.Nullable;

/**
 * A data source that maps {@link HederaKey}s to values.
 *
 * <p>A returned value may represent either a logical <i>entity</i> (e.g., an account);
 * or a <i>property</i> of such an entity (e.g. an account's balance). In all current
 * use cases, primitive values ({@code boolean}, {@code int}, {@code long}) are properties,
 * not entities.
 *
 * <p><b>IMPORTANT:</b> null object values are not allowed.
 */
public interface ReadOnlyKeyValueStore {
    /**
     * Gets an object-valued mapping for the given key, which must exist in this store.
     *
     * @param key the desired key
     * @return the mapped value
     * @param <V> the type of the value
     * @throws IllegalArgumentException if there is no such key
     */
    <V> V get(HederaObjectKey<V> key);

    /**
     * Gets an object-valued mapping for the given key, if it exists. There are no
     * primitive specializations of this method because its main use case is to check
     * for entity existence, and primitive values represent entity properties.
     *
     * @param key the desired key
     * @return the mapped value, or null if no mapping exists
     * @param <V> the type of the value
     */
    @Nullable
    <V> V getIfPresent(HederaObjectKey<V> key);

    /**
     * Gets a int value mapped to the given key, which must exist in this store.
     *
     * @param key the desired key
     * @return the mapped int
     * @throws IllegalArgumentException if there is no such key
     */
    int getInt(HederaIntKey key);

    /**
     * Gets a long value mapped to the given key, which must exist in this store.
     *
     * @param key the desired key
     * @return the mapped long
     * @throws IllegalArgumentException if there is no such key
     */
    long getLong(HederaLongKey key);

    /**
     * Gets a boolean value mapped to the given key, which must exist in this store.
     *
     * @param key the desired key
     * @return the mapped int
     * @throws IllegalArgumentException if there is no such key
     */
    boolean getBoolean(HederaBooleanKey key);
}
