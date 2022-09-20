package com.hedera.services.ledger.v2.support;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;


/**
 * Provides helpers to access all entity types in a {@link ServicesState}.
 *
 * <p>This level of indirection lets implementations cache entities fetched via
 * {@link MerkleMap#get(Object)}, which acquires a lock. In the future this extra
 * indirection may be unnecessary.
 */
public interface StateAccess {
    MerkleAccount getAccount(EntityNum num);

    MerkleTokenRelStatus getTokenRel(EntityNumPair relNums);

    RecordsRunningHashLeaf getRunningHashes();

    MerkleNetworkContext getNetworkContext();

    /**
     * Lots more...
     */
}
