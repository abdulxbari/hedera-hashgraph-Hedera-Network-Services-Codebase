package com.hedera.services.ledger.v2.keys;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.support.ChangeImpactType;
import com.hedera.services.ledger.v2.support.StateAccess;

import java.util.EnumSet;
import java.util.Set;

import static com.hedera.services.ledger.v2.support.ChangeImpactType.UNCLASSIFIED;

public interface HederaKey {
    Set<ChangeImpactType> UNCLASSIFIED_CHANGE_IMPACT = EnumSet.of(UNCLASSIFIED);

    /**
     * Forces every key type to define a good hash code implementation, where "good" means
     * "likely to be uniformly distributed over the set of all {@code HederaKey} implementations".
     *
     * @return a good hash code
     */
    @Override
    int hashCode();

    /**
     * Forces every key type to define a value-based equals implementation.
     *
     * @return whether this key is equal to the given object
     */
    @Override
    boolean equals(Object o);

    /**
     * Returns the types of impacts entailed by changing this key's mapping. (For example, changing
     * an account's key or alias has an impact on {@link ChangeImpactType#SIGNATURE_VERIFICATION}.)
     *
     * @return the impact types for this key's mapping
     */
    default Set<ChangeImpactType> getChangeImpactTypes() {
        return UNCLASSIFIED_CHANGE_IMPACT;
    }

    // All subsequent methods are to avoid auto-boxing when getting or mutating primitive values
    /**
     * Checks if this key maps to a long value.
     *
     * @return whether the mapped value is a long
     */
    default boolean isLong() {
        return false;
    }

    /**
     * Extracts the long value for this key from the given accessible state.
     *
     * @param stateAccess access to a state of interest
     * @return the value of this key in the given state
     * @throws IllegalArgumentException if the key does not exist in the state
     */
    default long getLong(final StateAccess stateAccess) {
        throw new UnsupportedOperationException();
    }

    /**
     * Maps this key to a desired long in the given state, which must be mutable.
     *
     * @param state the mutable state
     * @return the desired value of this key
     * @throws IllegalArgumentException if the mapping cannot be set
     */
    default void setLong(final ServicesState state, final long value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks if this key maps to a int value.
     *
     * @return whether the mapped value is a int
     */
    default boolean isInt() {
        return false;
    }

    /**
     * Extracts the int value for this key from the given Merkle state as an object reference.
     *
     * @param stateAccess access to a state of interest
     * @return the value of this key in the given state
     * @throws com.hedera.services.exceptions.InvalidTransactionException if the key does not exist in the state
     */
    default int getInt(StateAccess stateAccess) {
        throw new UnsupportedOperationException();
    }

    default void setInt(StateAccess stateAccess, int value) {
        throw new UnsupportedOperationException();
    }

    default boolean isBoolean() {
        return false;
    }

    /**
     * Extracts the boolean value for this key from the given Merkle state as an object reference.
     *
     * @param stateAccess access to a state of interest
     * @return the boolean value of this key in the given state
     * @throws com.hedera.services.exceptions.InvalidTransactionException if the key does not exist in the state
     */
    default boolean getBoolean(StateAccess stateAccess) {
        throw new UnsupportedOperationException();
    }

    default void setBoolean(StateAccess stateAccess, boolean value) {
        throw new UnsupportedOperationException();
    }
}
