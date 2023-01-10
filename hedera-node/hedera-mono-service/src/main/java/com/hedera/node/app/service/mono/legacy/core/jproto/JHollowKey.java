/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.legacy.core.jproto;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;

public class JHollowKey extends JKey {
    private byte[] evmAddress;

    public byte[] getEvmAddress() {
        return evmAddress;
    }

    public JHollowKey(final byte[] evmAddress) {
        this.evmAddress = evmAddress;
    }

    @Override
    public boolean isEmpty() {
        return ((null == evmAddress) || (0 == evmAddress.length));
    }

    @Override
    public boolean isValid() {
        return !(isEmpty() || (evmAddress.length != EVM_ADDRESS_SIZE));
    }

    @Override
    public boolean hasHollowKey() {
        return true;
    }

    @Override
    public JHollowKey getHollowKey() {
        return this;
    }
}
