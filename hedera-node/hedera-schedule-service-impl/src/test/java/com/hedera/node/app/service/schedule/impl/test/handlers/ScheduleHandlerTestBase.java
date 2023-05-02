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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.swirlds.config.api.Configuration;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NewClassNamingConvention")
@ExtendWith(MockitoExtension.class)
class ScheduleHandlerTestBase {
    // A few random values for fake ed25519 test keys
    public static final String PAYER_KEY_HEX =
            "badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada";
    public static final String SCHEDULER_KEY_HEX =
            "feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad";
    // This one is a perfect 10.
    public static final String ADMIN_KEY_HEX =
            "0000000000191561942608236107294793378084303638130997321548169216";
    protected final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(100L).build();
    protected AccountID adminAccount = AccountID.newBuilder().accountNum(626068L).build();
    protected Key adminKey = Key.newBuilder().ed25519(Bytes.fromHex(ADMIN_KEY_HEX)).build();
    protected AccountID scheduler = AccountID.newBuilder().accountNum(1001L).build();
    protected Key schedulerKey = Key.newBuilder().ed25519(Bytes.fromHex(SCHEDULER_KEY_HEX)).build();
    protected AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    protected Key payerKey = Key.newBuilder().ed25519(Bytes.fromHex(PAYER_KEY_HEX)).build();

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account schedulerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected Account payerAccount;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableAccountStore accountStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ScheduleVirtualValue scheduleInState;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ReadableStoreFactory mockStoreFactory;

    // Non-Mock objects, but may contain or reference mock objects.
    protected ReadableScheduleStore scheduleStore;
    protected Configuration testConfig;
    protected TransactionBody scheduled;

    protected void setUpBase() throws PreCheckException, InvalidKeyException {
        testConfig = new HederaTestConfigBuilder().getOrCreateConfig();
        scheduled = createSampleScheduled();
        BDDMockito.given(states.<Long, ScheduleVirtualValue>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        BDDMockito.given(schedulerAccount.key()).willReturn(schedulerKey);
        BDDMockito.given(payerAccount.key()).willReturn(payerKey);

        scheduleStore = new ReadableScheduleStoreImpl(states);

        BDDMockito.given(accountStore.getAccountById(scheduler)).willReturn(schedulerAccount);
        BDDMockito.given(accountStore.getAccountById(payer)).willReturn(payerAccount);

        Optional<JKey> adminJKey = Optional.of(JKey.convertKey(PbjConverter.fromPbj(adminKey), 10));
        // @migration this use of PbjConverter is temporary until services complete PBJ migration
        BDDMockito.given(scheduleInState.hasExplicitPayer()).willReturn(Boolean.TRUE);
        BDDMockito.given(scheduleInState.payer()).willReturn(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(payer)));
        BDDMockito.given(scheduleInState.schedulingAccount()).willReturn(EntityId.fromGrpcAccountId(PbjConverter.fromPbj(payer)));
        BDDMockito.given(scheduleInState.hasAdminKey()).willReturn(adminJKey.isPresent());
        BDDMockito.given(scheduleInState.adminKey()).willReturn(adminJKey);
        BDDMockito.given(scheduleInState.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduled));
        BDDMockito.given(schedulesById.get(testScheduleID.scheduleNum())).willReturn(scheduleInState);

        BDDMockito.given(mockStoreFactory.getStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        BDDMockito.given(mockStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
    }

    protected TransactionBody createSampleScheduled() throws PreCheckException {
        final TransactionBody scheduledTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
                .cryptoCreateAccount(CryptoCreateTransactionBody.newBuilder().build())
                .build();
        return scheduledTxn;
    }

    protected TransactionBody scheduleNotRecognized() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(SchedulableTransactionBody.newBuilder()
                                .utilPrng(UtilPrngTransactionBody.DEFAULT)
                                .build()))
                .build();
    }

    protected ScheduleVirtualValue testStateValue() {
        return ScheduleVirtualValue.from(extantSchedulingBodyBytes(), 1801L);
    }

    private byte[] extantSchedulingBodyBytes() {
        return com.hedera.test.factories.txns.ScheduledTxnFactory.scheduleCreateTxnWith(
                        com.hederahashgraph.api.proto.java.Key.getDefaultInstance(),
                        "",
                        TxnHandlingScenario.MISC_ACCOUNT,
                        TxnHandlingScenario.MISC_ACCOUNT,
                        MiscUtils.asTimestamp(Instant.ofEpochSecond(1L)))
                .toByteArray();
    }
}
