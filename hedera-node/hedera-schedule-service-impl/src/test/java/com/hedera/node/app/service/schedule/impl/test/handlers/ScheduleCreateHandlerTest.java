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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.test.ScheduledTxnFactory;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {
    private ScheduleCreateHandler subject;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws Exception {
        subject = new ScheduleCreateHandler();
        setUpBase();
    }

    @Test
    void preHandleScheduleCreateVanilla() throws PreCheckException {
        realPreContext = new PreHandleContextImpl(mockStoreFactory, scheduleCreateTransaction(payer), testConfig);

        subject.preHandle(realPreContext);

        org.junit.jupiter.api.Assertions.assertEquals(2, realPreContext.requiredNonPayerKeys().size());
        org.junit.jupiter.api.Assertions.assertEquals(schedulerKey, realPreContext.payerKey());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(adminKey, payerKey), realPreContext.requiredNonPayerKeys());
    }

    @Test
    void preHandleScheduleCreateVanillaNoAdmin() throws PreCheckException {
        final TransactionBody transactionToTest = ScheduledTxnFactory.scheduleCreateTxnWith(
                null, "", payer, scheduler, Timestamp.newBuilder().seconds(1L).build());
        realPreContext = new PreHandleContextImpl(mockStoreFactory, transactionToTest, testConfig);

        subject.preHandle(realPreContext);

        org.junit.jupiter.api.Assertions.assertEquals(1, realPreContext.requiredNonPayerKeys().size());
        org.junit.jupiter.api.Assertions.assertEquals(schedulerKey, realPreContext.payerKey());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(payerKey), realPreContext.requiredNonPayerKeys());

    }

    @Test
    void preHandleScheduleCreateUsesSamePayerIfScheduledPayerNotSet() throws PreCheckException {
        realPreContext = new PreHandleContextImpl(mockStoreFactory, scheduleCreateTransaction(null), testConfig);

        subject.preHandle(realPreContext);

        org.junit.jupiter.api.Assertions.assertEquals(1, realPreContext.requiredNonPayerKeys().size());
        org.junit.jupiter.api.Assertions.assertEquals(schedulerKey, realPreContext.payerKey());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(adminKey), realPreContext.requiredNonPayerKeys());

    }

    @Test
    void failsWithScheduleTransactionNotInWhitelist() throws PreCheckException {
        scheduled = scheduleNotRecognized();
        BDDMockito.given(scheduleInState.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(scheduled));
        realPreContext = new PreHandleContextImpl(mockStoreFactory, scheduleNotRecognized(), testConfig);

//        Assertions.assertThrowsPreCheck(() ->
//                subject.preHandle(realPreContext), ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
//
//        org.junit.jupiter.api.Assertions.assertEquals(0, realPreContext.requiredNonPayerKeys().size());
//        org.junit.jupiter.api.Assertions.assertEquals(schedulerKey, realPreContext.payerKey());
//        org.junit.jupiter.api.Assertions.assertEquals(Collections.emptySet(), realPreContext.requiredNonPayerKeys());

        // @todo whitelist tests don't work; it appears they never actually tested a
        //       non-whitelist situation so much as a missing key.  This requires careful
        //       thought and rework.
        //       Specifically, the scheduled transaction body can't have a type that isn't
        //       supported, as it is currently defined, so it's not possible to fail due to whitelist.
        //
        //        final var innerContext = context.innerContext();
        //        basicContextAssertions(innerContext, 0, true,
        // SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        //        assertEquals(scheduler, innerContext.payer());
        //        assertEquals(schedulerKey, innerContext.payerKey());
        //        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void innerTxnFailsSetsStatus() throws PreCheckException {
        BDDMockito.given(accountStore.getAccountById(payer)).willReturn(null);

        realPreContext = new PreHandleContextImpl(mockStoreFactory, scheduleCreateTransaction(payer), testConfig);
        Assertions.assertThrowsPreCheck(() ->
                subject.preHandle(realPreContext), ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        final Timestamp timestampValue =
                Timestamp.newBuilder().seconds(1_234_567L).build();
        return ScheduledTxnFactory.scheduleCreateTxnWith(adminKey, "test", payer, scheduler, timestampValue);
    }
}
