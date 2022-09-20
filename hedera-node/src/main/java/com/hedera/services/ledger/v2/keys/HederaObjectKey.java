package com.hedera.services.ledger.v2.keys;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.support.StateAccess;

/**
 * Represents a hashable key able to extract a value from a Merkle state.
 *
 * @param <V> the type of the key's value
 */
public interface HederaObjectKey<V> extends HederaKey {
    /**
     * Extracts the object value for this key from the given Merkle state.
     *
     * @param state a state of interest
     * @return the value of this key in the given state
     * @throws com.hedera.services.exceptions.InvalidTransactionException if the key does not exist
     */
    V getObject(StateAccess stateAccess);

    /**
     * Sets the given object as this key's value in the given Merkle state.
     *
     * @param state a state of interest
     * @throws com.hedera.services.exceptions.InvalidTransactionException if the value cannot be set
     */
    void setObject(ServicesState state, V value);
}
