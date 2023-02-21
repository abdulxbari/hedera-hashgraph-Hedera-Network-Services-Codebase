/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.mono.ledger.backing;

import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.store.models.NftId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingNfts implements BackingStore<NftId, UniqueTokenAdapter> {
    private Map<NftId, UniqueTokenAdapter> nfts = new HashMap<>();

    @Override
    public UniqueTokenAdapter getRef(NftId id) {
        return nfts.get(id);
    }

    @Override
    public UniqueTokenAdapter getImmutableRef(NftId id) {
        return nfts.get(id);
    }

    @Override
    public void put(NftId id, UniqueTokenAdapter nft) {
        nfts.put(id, nft);
    }

    @Override
    public void remove(NftId id) {
        nfts.remove(id);
    }

    @Override
    public boolean contains(NftId id) {
        return nfts.containsKey(id);
    }

    @Override
    public Set<NftId> idSet() {
        return nfts.keySet();
    }

    @Override
    public long size() {
        return nfts.size();
    }
}
