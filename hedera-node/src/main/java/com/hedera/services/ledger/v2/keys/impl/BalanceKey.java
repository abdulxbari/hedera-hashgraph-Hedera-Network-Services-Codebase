package com.hedera.services.ledger.v2.keys.impl;

import com.hedera.services.ServicesState;
import com.hedera.services.ledger.v2.keys.HederaLongKey;
import com.hedera.services.ledger.v2.support.StateAccess;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;

public class BalanceKey implements HederaLongKey {
    private static long NOMINAL_HBAR_TOKEN_ID = 0;

    private long accountNum;
    private long tokenNum = NOMINAL_HBAR_TOKEN_ID;

    @Override
    public long getLong(StateAccess stateAccess) {
        return tokenNum == NOMINAL_HBAR_TOKEN_ID
                ? stateAccess.getAccount(EntityNum.fromLong(accountNum)).getBalance()
                : stateAccess.getTokenRel(EntityNumPair.fromLongs(accountNum, tokenNum)).getBalance();
    }

    @Override
    public void setLong(ServicesState state, long value) {

    }
}
