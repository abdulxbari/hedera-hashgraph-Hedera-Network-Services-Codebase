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

import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.VALID_GRANT_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import org.junit.jupiter.api.Test;

class TokenGrantKycToAccountHandlerTest extends ParityTestBase {
    private final TokenGrantKycToAccountHandler subject = new TokenGrantKycToAccountHandler();

    @Test
    void tokenValidGrantWithExtantTokenScenario() {
        final var theTxn = txnFrom(VALID_GRANT_WITH_EXTANT_TOKEN);

        final var meta =
                subject.preHandle(
                        theTxn,
                        theTxn.getTransactionID().getAccountID(),
                        keyLookup,
                        readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(sanityRestored(meta.requiredNonPayerKeys()), contains(TOKEN_KYC_KT.asKey()));
    }
}
