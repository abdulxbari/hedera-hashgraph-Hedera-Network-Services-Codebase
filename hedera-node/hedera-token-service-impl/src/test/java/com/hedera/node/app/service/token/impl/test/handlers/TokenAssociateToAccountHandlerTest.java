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

import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class TokenAssociateToAccountHandlerTest extends ParityTestBase {

    private final TokenAssociateToAccountHandler subject = new TokenAssociateToAccountHandler();

    @Test
    void tokenAssociateWithKnownTargetScenario() {
        final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

        final var meta =
                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(), keyLookup);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                Matchers.contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void tokenAssociateWithSelfPaidKnownTargetScenario() {
        final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        final var meta =
                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(), keyLookup);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(0, meta.requiredNonPayerKeys().size());
    }

    @Test
    void tokenAssociateWithCustomPaidKnownTargetScenario() {
        final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        final var meta =
                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(), keyLookup);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                Matchers.contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void tokenAssociateWithImmutableTargetScenario() {
        final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET);

        final var meta =
                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(), keyLookup);

        assertTrue(meta.failed());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());
        assertEquals(0, meta.requiredNonPayerKeys().size());
    }

    @Test
    void tokenAssociateWithMissingTargetScenario() {
        final var theTxn = txnFrom(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

        final var meta =
                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(), keyLookup);

        assertTrue(meta.failed());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());
        assertEquals(0, meta.requiredNonPayerKeys().size());
    }
}
