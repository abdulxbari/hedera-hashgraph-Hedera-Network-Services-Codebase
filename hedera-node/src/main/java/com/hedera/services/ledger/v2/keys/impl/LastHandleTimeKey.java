package com.hedera.services.ledger.v2.keys.impl;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.keys.HederaObjectKey;
import com.hedera.services.ledger.v2.support.StateAccess;

import java.time.Instant;

public enum LastHandleTimeKey implements HederaObjectKey<Instant> {
    LAST_HANDLE_TIME_KEY;

    @Override
    public Instant getObject(final StateAccess stateAccess) {
        return stateAccess.getNetworkContext().consensusTimeOfLastHandledTxn();
    }

    @Override
    public void setObject(final ServicesState state, final Instant value) {
        state.networkCtx().setConsensusTimeOfLastHandledTxn(value);
    }
}
