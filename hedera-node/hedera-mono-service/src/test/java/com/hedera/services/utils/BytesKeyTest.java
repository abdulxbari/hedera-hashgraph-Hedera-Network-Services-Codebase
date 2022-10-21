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
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.evm.store.contracts.utils.BytesKey;
import com.swirlds.common.utility.CommonUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

class BytesKeyTest {
    @Test
    void toStringIncorporatesArrayContents() {
        final var literal = Hex.decode("abcdef");
        final var desired = "BytesKey[array=" + CommonUtils.hex(literal) + "]";

        final var subject = new BytesKey(literal);

        assertEquals(desired, subject.toString());
    }
}
