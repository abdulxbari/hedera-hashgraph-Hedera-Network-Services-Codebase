/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.regression.factories.LazyCreatePrecompileFuzzingFactory.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;

public class RandomLazyCreateFungibleTransfer implements OpProvider {
    private final HapiSpecRegistry registry;
    private static final long GAS_TO_OFFER = 5_000_000L;

    private final EntityNameProvider<Key> keys;

    public RandomLazyCreateFungibleTransfer(HapiSpecRegistry registry, EntityNameProvider<Key> keys) {
        this.registry = registry;
        this.keys = keys;
    }

    private Optional<String> randomKey() {
        return keys.getQualifying().filter(k -> !k.isEmpty());
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        return randomKey().map(this::generateLazyCreateTransferOfFungibleToken);
    }

    private HapiContractCall generateLazyCreateTransferOfFungibleToken(String evmAddressRecipient) {
        final var addressBytes = recoverAddressFromPubKey(getEvmAddress(evmAddressRecipient));
        return contractCall(
                        TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT,
                        "transferTokenCallNestedThenAgain",
                        HapiParserUtil.asHeadlongAddress(asAddress(registry.getTokenID(FUNGIBLE_TOKEN))),
                        HapiParserUtil.asHeadlongAddress(asAddress(registry.getAccountID(OWNER))),
                        HapiParserUtil.asHeadlongAddress(addressBytes),
                        2L,
                        2L)
                .via(TRANSFER_TOKEN_TXN)
                .alsoSigningWithFullPrefix(OWNER)
                .gas(GAS_TO_OFFER)
                .hasKnownStatus(SUCCESS);
    }

    private byte[] getEvmAddress(String keyName) {
        return this.registry.getKey(keyName).getECDSASecp256K1().toByteArray();
    }
}