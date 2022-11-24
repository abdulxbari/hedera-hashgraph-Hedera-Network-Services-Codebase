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
package com.hedera.services.state.virtual.temporal;

import static com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKeySupplier.CLASS_ID;
import static com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKeySupplier.CURRENT_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKeySupplier;
import org.junit.jupiter.api.Test;

class SecondSinceEpocVirtualKeySupplierTest {
    private SecondSinceEpocVirtualKeySupplier subject = new SecondSinceEpocVirtualKeySupplier();

    @Test
    void gettersWork() {
        assertEquals(CLASS_ID, subject.getClassId());
        assertEquals(CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void delegatesAsExpected() {
        var virtualKey = subject.get();

        assertEquals(SecondSinceEpocVirtualKey.CLASS_ID, virtualKey.getClassId());
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize(null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
