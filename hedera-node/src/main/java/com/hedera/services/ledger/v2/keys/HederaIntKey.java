package com.hedera.services.ledger.v2.keys;

import com.hedera.services.ledger.v2.support.StateAccess;

public interface HederaIntKey extends HederaKey {
    @Override
    default boolean isInt() {
        return true;
    }

    @Override
    int getInt(StateAccess state);

    @Override
    void setInt(StateAccess state, int value);
}
