/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class TxnResourceUsageEstimatorTest {
    @Test
    void defaultResourceUsageEstimatorHasNoSecondaryFees() {
        final var mockSubject = mock(TxnResourceUsageEstimator.class);
        final var txn = TransactionBody.getDefaultInstance();

        willCallRealMethod().given(mockSubject).hasSecondaryFees();
        willCallRealMethod().given(mockSubject).secondaryFeesFor(any());

        assertFalse(mockSubject.hasSecondaryFees());
        verify(mockSubject).hasSecondaryFees();
        assertThrows(UnsupportedOperationException.class, () -> mockSubject.secondaryFeesFor(txn));
    }
}
