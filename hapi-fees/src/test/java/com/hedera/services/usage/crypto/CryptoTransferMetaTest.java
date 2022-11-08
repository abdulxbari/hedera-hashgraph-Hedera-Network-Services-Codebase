/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.crypto;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hederahashgraph.api.proto.java.SubType;
import org.junit.jupiter.api.Test;

class CryptoTransferMetaTest {

    @Test
    void setterWith4ParamsWorks() {
        final var subject = new CryptoTransferMeta(1, 2, 3, 4);

        // when:
        subject.setCustomFeeHbarTransfers(10);
        subject.setCustomFeeTokenTransfers(5);
        subject.setCustomFeeTokensInvolved(2);

        // then:
        assertEquals(1, subject.getTokenMultiplier());
        assertEquals(3, subject.getNumFungibleTokenTransfers());
        assertEquals(2, subject.getNumTokensInvolved());
        assertEquals(4, subject.getNumNftOwnershipChanges());
        assertEquals(2, subject.getCustomFeeTokensInvolved());
        assertEquals(5, subject.getCustomFeeTokenTransfers());
        assertEquals(10, subject.getCustomFeeHbarTransfers());
    }

    @Test
    void getSubTypePrioritizesNFT() {
        var subject = new CryptoTransferMeta(1, 2, 3, 4);

        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSubType());

        subject.setCustomFeeHbarTransfers(0);
        subject.setCustomFeeTokenTransfers(5);
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());

        subject.setCustomFeeHbarTransfers(10);
        subject.setCustomFeeTokenTransfers(0);
        assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());

        subject = new CryptoTransferMeta(1, 2, 3, 0);

        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON, subject.getSubType());

        subject.setCustomFeeHbarTransfers(0);
        subject.setCustomFeeTokenTransfers(5);
        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());

        subject.setCustomFeeHbarTransfers(10);
        subject.setCustomFeeTokenTransfers(0);
        assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());

        subject = new CryptoTransferMeta(1, 0, 0, 0);

        assertEquals(SubType.DEFAULT, subject.getSubType());
    }
}
