package com.hedera.services.ledger.v2.keys.impl;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.keys.HederaObjectKey;
import com.hedera.services.ledger.v2.support.StateAccess;
import com.swirlds.common.crypto.RunningHash;

public enum RunningHashKey implements HederaObjectKey<RunningHash> {
    RUNNING_HASH {
        @Override
        public RunningHash getObject(StateAccess stateAccess) {
            return stateAccess.getRunningHashes().getRunningHash();
        }

        @Override
        public void setObject(ServicesState state, RunningHash value) {
            state.runningHashLeaf().setRunningHash(value);
        }
    },
    N_MINUS_THREE_RUNNING_HASH {
        @Override
        public RunningHash getObject(StateAccess stateAccess) {
            return stateAccess.getRunningHashes().getNMinus3RunningHash();
        }

        @Override
        public void setObject(ServicesState state, RunningHash value) {
           throw new UnsupportedOperationException();
        }
    };
}
