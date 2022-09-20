package com.hedera.services.ledger.v2.keys;

import com.hedera.services.ledger.v2.support.StateAccess;

public interface HederaBooleanKey extends HederaKey {
    @Override
    default boolean isBoolean() {
        return true;
    }

    @Override
    boolean getBoolean(StateAccess stateAccess);

    @Override
    void setBoolean(StateAccess stateAccess, boolean value);
}
