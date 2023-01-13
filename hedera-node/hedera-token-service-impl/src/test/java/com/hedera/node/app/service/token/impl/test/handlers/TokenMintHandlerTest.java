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
package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_FOR_TOKEN_WITHOUT_SUPPLY;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import org.junit.jupiter.api.Test;

class TokenMintHandlerTest extends ParityTestBase {
    private final TokenMintHandler subject = new TokenMintHandler();

    @Test
    void tokenMintWithSupplyKeyedTokenScenario() {
        final var theTxn = txnFrom(MINT_WITH_SUPPLY_KEYED_TOKEN);

        final var meta =
                subject.preHandle(
                        theTxn,
                        theTxn.getTransactionID().getAccountID(),
                        keyLookup,
                        readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(sanityRestored(meta.requiredNonPayerKeys()), contains(TOKEN_SUPPLY_KT.asKey()));
    }

    @Test
    void tokenMintWithMissingTokenScenario() {
        final var theTxn = txnFrom(MINT_WITH_MISSING_TOKEN);

        final var meta =
                subject.preHandle(
                        theTxn,
                        theTxn.getTransactionID().getAccountID(),
                        keyLookup,
                        readableTokenStore);

        assertTrue(meta.failed());
        assertEquals(INVALID_TOKEN_ID, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, meta.requiredNonPayerKeys().size());
    }

    @Test
    void tokenMintWithoutSupplyScenario() {
        final var theTxn = txnFrom(MINT_FOR_TOKEN_WITHOUT_SUPPLY);

        final var meta =
                subject.preHandle(
                        theTxn,
                        theTxn.getTransactionID().getAccountID(),
                        keyLookup,
                        readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(0, meta.requiredNonPayerKeys().size());
    }
}
