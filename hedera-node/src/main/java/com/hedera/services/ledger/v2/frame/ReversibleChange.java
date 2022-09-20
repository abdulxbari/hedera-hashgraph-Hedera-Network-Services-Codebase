package com.hedera.services.ledger.v2.frame;

import com.hedera.services.ledger.v2.keys.HederaKey;

public interface ReversibleChange<V> {
    HederaKey key();
    V oldValue();
    V newValue();

    default boolean isIntChange() {
        return false;
    }

    default boolean isLongChange() {
        return false;
    }

    default boolean isBooleanChange() {
        return false;
    }

    default int oldIntValue() {
        throw new UnsupportedOperationException();
    }

    default int newIntValue() {
        throw new UnsupportedOperationException();
    }

    default long oldLongValue() {
        throw new UnsupportedOperationException();
    }

    default long newLongValue() {
        throw new UnsupportedOperationException();
    }

    default boolean oldBooleanValue() {
        throw new UnsupportedOperationException();
    }

    default boolean newBooleanValue() {
        throw new UnsupportedOperationException();
    }
}
