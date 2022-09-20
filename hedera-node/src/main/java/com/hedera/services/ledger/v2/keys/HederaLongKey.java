package com.hedera.services.ledger.v2.keys;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.support.StateAccess;

public interface HederaLongKey extends HederaKey {
    @Override
    default boolean isLong() {
        return true;
    }

    @Override
    long getLong(StateAccess stateAccess);

    @Override
    void setLong(ServicesState state, long value);
}
