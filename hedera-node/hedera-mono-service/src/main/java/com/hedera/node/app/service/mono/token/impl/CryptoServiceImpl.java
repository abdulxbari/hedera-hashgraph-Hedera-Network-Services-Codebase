/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/** An implementation of the {@link CryptoService} interface. */
public final class CryptoServiceImpl implements CryptoService {
    @NonNull
    @Override
    public CryptoPreTransactionHandler createPreTransactionHandler(
            @NonNull final ReadableStates states, @NonNull final PreHandleContext ctx) {
        Objects.requireNonNull(states);
        Objects.requireNonNull(ctx);
        final var store = new AccountStore(states);
        return new CryptoPreTransactionHandlerImpl(store, ctx);
    }

    @NonNull
    @Override
    public CryptoQueryHandler createQueryHandler(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states);
        return new CryptoQueryHandlerImpl(states);
    }
}