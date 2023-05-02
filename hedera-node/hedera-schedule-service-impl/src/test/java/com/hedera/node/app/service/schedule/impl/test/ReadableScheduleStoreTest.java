/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.KeyUtils;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class ReadableScheduleStoreTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    ReadableKVState state;

    @Mock(strictness = Mock.Strictness.LENIENT)
    ScheduleVirtualValue schedule;

    private ReadableScheduleStore subject;
    private Key adminKey = KeyUtils.A_COMPLEX_KEY;
    private HederaKey adminHederaKey = Utils.asHederaKey(adminKey).get();

    @BeforeEach
    void setUp() {
        BDDMockito.given(states.get("SCHEDULES_BY_ID")).willReturn(state);
        subject = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(state.get(1L)).willReturn(schedule);
        BDDMockito.given(schedule.ordinaryViewOfScheduledTxn())
                .willReturn(PbjConverter.fromPbj(TransactionBody.newBuilder().build()));
        BDDMockito.given(schedule.hasAdminKey()).willReturn(true);
        BDDMockito.given(schedule.adminKey()).willReturn(Optional.of((JKey) adminHederaKey));
        BDDMockito.given(schedule.hasExplicitPayer()).willReturn(true);
        BDDMockito.given(schedule.payer()).willReturn(EntityId.fromNum(2L));
        BDDMockito.given(schedule.schedulingAccount()).willReturn(EntityId.fromGrpcAccountId(
                TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void constructorThrowsIfStatesIsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> new ReadableScheduleStoreImpl(null));
    }

    @Test
    void returnsEmptyIfMissingSchedule() {
        BDDMockito.given(state.get(1L)).willReturn(null);

        Assertions.assertEquals(
                Optional.empty(),
                subject.get(ScheduleID.newBuilder().scheduleNum(1L).build()));
    }

    @Test
    void getsScheduleMetaFromFetchedSchedule() {
        final var meta = subject.get(ScheduleID.newBuilder().scheduleNum(1L).build());

        Assertions.assertEquals(adminKey, meta.get().adminKey());
        Assertions.assertEquals(TransactionBody.newBuilder().build(), meta.get().scheduledTxn());
        Assertions.assertEquals(
                Optional.of(EntityId.fromNum(2L).toPbjAccountId()), meta.get().designatedPayer());
    }

    @Test
    void getsScheduleMetaFromFetchedScheduleNoExplicitPayer() {
        BDDMockito.given(schedule.hasExplicitPayer()).willReturn(false);
        BDDMockito.given(schedule.payer()).willReturn(null);

        final var meta = subject.get(ScheduleID.newBuilder().scheduleNum(1L).build());

        Assertions.assertEquals(adminKey, meta.get().adminKey());
        Assertions.assertEquals(TransactionBody.newBuilder().build(), meta.get().scheduledTxn());
        Assertions.assertEquals(Optional.empty(), meta.get().designatedPayer());
    }
}
