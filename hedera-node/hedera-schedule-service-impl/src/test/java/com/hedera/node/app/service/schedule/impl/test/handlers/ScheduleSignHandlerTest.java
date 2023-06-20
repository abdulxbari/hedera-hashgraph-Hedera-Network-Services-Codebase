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

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import org.assertj.core.api.BDDAssertions;
import com.hedera.node.app.spi.fixtures.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class ScheduleSignHandlerTest extends ScheduleHandlerTestBase {
    private ScheduleSignHandler subject;


    private PreHandleContext realPreContext;
    private TransactionBody scheduled;

    @BeforeEach
    void setUp() throws Exception {
        subject = new ScheduleSignHandler();
        setUpBase();
    }

    @Test
    void scheduleSignVanillaNoExplicitPayer() throws PreCheckException {
        final TransactionBody testTransaction = scheduleSignTransaction(null);
        realPreContext = new PreHandleContextImpl(mockStoreFactory, testTransaction, testConfig);

        subject.preHandle(realPreContext);
        BDDAssertions.assertThat(scheduler).isEqualTo(realPreContext.payer());
        BDDAssertions.assertThat(schedulerKey).isEqualTo(realPreContext.payerKey());
        BDDAssertions.assertThat(Collections.emptySet()).isNotEqualTo(realPreContext.requiredNonPayerKeys());
    }

    @Test
    void scheduleSignFailsIfScheduleMissing() throws PreCheckException {
        final ScheduleID badScheduleID = ScheduleID.newBuilder().scheduleNum(1L).build();
        final TransactionBody testTransaction = scheduleSignTransaction(badScheduleID);
        realPreContext = new PreHandleContextImpl(mockStoreFactory, testTransaction, testConfig);
        Assertions.assertThrowsPreCheck(() -> subject.preHandle(realPreContext),
                ResponseCodeEnum.INVALID_SCHEDULE_ID);
    }

    @Test
    void scheduleSignVanillaWithOptionalPayerSet() throws PreCheckException {
        final TransactionBody testTransaction = scheduleSignTransaction(null);
        realPreContext = new PreHandleContextImpl(mockStoreFactory, testTransaction, testConfig);
        subject.preHandle(realPreContext);
        BDDAssertions.assertThat(scheduler).isEqualTo(realPreContext.payer());
        BDDAssertions.assertThat(schedulerKey).isEqualTo(realPreContext.payerKey());

    }

    @Test
    void scheduleSignForNotSchedulableFails() throws PreCheckException {
        final TransactionBody invalidScheduled = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler).build())
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder().build())
                .build();
        BDDMockito.given(scheduleInState.ordinaryViewOfScheduledTxn())
                .willReturn(PbjConverter.fromPbj(invalidScheduled));

        realPreContext = new PreHandleContextImpl(mockStoreFactory, invalidScheduled, testConfig);
        Assertions.assertThrowsPreCheck(() -> subject.preHandle(realPreContext),
                ResponseCodeEnum.INVALID_TRANSACTION_BODY);
    }

    // @todo Need to create a valid test for "schedule sign with key not in whitelist"
    //       (the prior test just checked for a missing key, which throws NPE now)

    private TransactionBody scheduleSignTransaction(@Nullable final ScheduleID idToUse) {
        final ScheduleID confirmedId = idToUse == null ? testScheduleID : idToUse;
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(scheduler))
                .scheduleSign(ScheduleSignTransactionBody.newBuilder().scheduleID(confirmedId))
                .build();
    }
}
