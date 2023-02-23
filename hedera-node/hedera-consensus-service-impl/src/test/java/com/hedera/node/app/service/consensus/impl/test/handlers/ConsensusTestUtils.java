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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.assertj.core.api.Assertions;

public final class ConsensusTestUtils {

    static final Key SIMPLE_KEY_A = Key.newBuilder()
            .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();
    static final Key SIMPLE_KEY_B = Key.newBuilder()
            .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))
            .build();

    private ConsensusTestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    static void assertOkResponse(PreHandleContext context) {
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.OK);
        assertThat(context.failed()).isFalse();
    }

    static HederaKey mockPayerLookup(Key key, AccountID accountId, AccountKeyLookup keyLookup) {
        final var returnKey = Utils.asHederaKey(key).orElseThrow();
        given(keyLookup.getKey(accountId)).willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }

    static void assertDefaultPayer(PreHandleContext context) {
        assertPayer(DEFAULT_PAYER_KT.asKey(), context);
    }

    static void assertCustomPayer(PreHandleContext context) {
        assertPayer(CUSTOM_PAYER_ACCOUNT_KT.asKey(), context);
    }

    static void assertPayer(Key expected, PreHandleContext context) {
        Assertions.assertThat(sanityRestored(context.getPayerKey())).isEqualTo(expected);
    }

    static TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            return fail(e);
        }
    }
}
