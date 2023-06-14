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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ABORT;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ONLY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.PREPARE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.TELEMETRY_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.UNKNOWN_FREEZE_TYPE;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.networkadmin.ReadableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.WritableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.WritableFreezeStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeHandlerTest {
    @Mock(strictness = LENIENT)
    WritableUpdateFileStore specialFileStore;

    @Mock(strictness = LENIENT)
    private WritableFreezeStore freezeStore;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private PreHandleContext preHandleContext;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private Account account;

    private final Key key = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();
    private final AccountID nonAdminAccount =
            AccountID.newBuilder().accountNum(9999L).build();
    private final FreezeHandler subject = new FreezeHandler();

    @BeforeEach
    void setUp() {
        Configuration config = new HederaTestConfigBuilder().getOrCreateConfig();
        given(preHandleContext.configuration()).willReturn(config);

        given(accountStore.getAccountById(nonAdminAccount)).willReturn(account);
        given(account.key()).willReturn(key);

        given(preHandleContext.createStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(preHandleContext.createStore(ReadableUpdateFileStore.class)).willReturn(specialFileStore);

        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableUpdateFileStore.class)).willReturn(specialFileStore);
        given(handleContext.writableStore(WritableFreezeStore.class)).willReturn(freezeStore);
    }

    @Test
    void pureChecksFailIfUnknownFreezeType() {
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder()
                        .freezeType(UNKNOWN_FREEZE_TYPE)
                        .build())
                .build();
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_FREEZE_TRANSACTION_BODY);
    }

    @Test
    void rejectIfStartTimeNotSetForCertainFreezeTypes() {
        // when using these freeze types, it is required to set start time
        FreezeType[] freezeTypes = {FREEZE_ONLY, FREEZE_UPGRADE, TELEMETRY_UPGRADE};
        for (FreezeType freezeType : freezeTypes) {
            TransactionBody txn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .accountID(nonAdminAccount)
                            .transactionValidStart(
                                    Timestamp.newBuilder().seconds(1000).build()))
                    .freeze(FreezeTransactionBody.newBuilder()
                            .freezeType(freezeType)
                            .build())
                    .build();
            given(preHandleContext.body()).willReturn(txn);
            assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_FREEZE_TRANSACTION_BODY);
        }
    }

    @Test
    void rejectIfStartTimeInPastForCertainFreezeTypes() {
        // when using these freeze types, it is required to set start time to a time after the effective consensus time
        FreezeType[] freezeTypes = {FREEZE_ONLY, FREEZE_UPGRADE, TELEMETRY_UPGRADE};

        for (FreezeType freezeType : freezeTypes) {
            TransactionID txnId = TransactionID.newBuilder()
                    .accountID(nonAdminAccount)
                    .transactionValidStart(Timestamp.newBuilder().seconds(2000).build())
                    .build();
            TransactionBody txn = TransactionBody.newBuilder()
                    .transactionID(txnId)
                    .freeze(FreezeTransactionBody.newBuilder()
                            .freezeType(freezeType)
                            .startTime(Timestamp.newBuilder().seconds(1000).build()))
                    .build();
            given(preHandleContext.body()).willReturn(txn);
            assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), FREEZE_START_TIME_MUST_BE_FUTURE);
        }
    }

    @Test
    void rejectIfUpdateFileNotSetForCertainFreezeTypes() {
        // when using these freeze types, it is required to set an update file
        FreezeType[] freezeTypes = {PREPARE_UPGRADE, FREEZE_UPGRADE, TELEMETRY_UPGRADE};
        for (FreezeType freezeType : freezeTypes) {
            TransactionID txnId = TransactionID.newBuilder()
                    .accountID(nonAdminAccount)
                    .transactionValidStart(Timestamp.newBuilder().seconds(1000).build())
                    .build();
            TransactionBody txn = TransactionBody.newBuilder()
                    .transactionID(txnId)
                    .freeze(FreezeTransactionBody.newBuilder()
                            .freezeType(freezeType)
                            .startTime(Timestamp.newBuilder().seconds(2000).build()))
                    .build();
            given(preHandleContext.body()).willReturn(txn);
            assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), FREEZE_UPDATE_FILE_DOES_NOT_EXIST);
        }
    }

    @Test
    void rejectIfFileHashNotSetForCertainFreezeTypes() {
        // when using these freeze types, it is required to set an update file
        FreezeType[] freezeTypes = {PREPARE_UPGRADE, FREEZE_UPGRADE, TELEMETRY_UPGRADE};

        FileID fileId = FileID.newBuilder().fileNum(1234L).build();
        given(specialFileStore.get(fileId)).willReturn(Optional.of(new byte[0]));

        for (FreezeType freezeType : freezeTypes) {
            TransactionID txnId = TransactionID.newBuilder()
                    .accountID(nonAdminAccount)
                    .transactionValidStart(Timestamp.newBuilder().seconds(1000).build())
                    .build();
            TransactionBody txn = TransactionBody.newBuilder()
                    .transactionID(txnId)
                    .freeze(FreezeTransactionBody.newBuilder()
                            .freezeType(freezeType)
                            .startTime(Timestamp.newBuilder().seconds(2000).build())
                            .updateFile(fileId)
                            .build())
                    .build();
            given(preHandleContext.body()).willReturn(txn);
            assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
        }
    }

    @Test
    void happyPathFreezeAbort() {
        // freeze_abort always returns OK, to allow the node to send multiple commands to abort
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder()
                        .freezeType(FREEZE_ABORT)
                        .build())
                .build();
        given(preHandleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));

        given(handleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void happyPathFreezeUpgradeOrTelemetryUpgrade() {
        // when using these freeze types, it is required to set an update file and file hash and they must match
        // also must set start time to a time after the effective consensus time
        FreezeType[] freezeTypes = {FREEZE_UPGRADE, TELEMETRY_UPGRADE};

        FileID fileId = FileID.newBuilder().fileNum(1234L).build();
        given(specialFileStore.get(fileId)).willReturn(Optional.of(new byte[0]));
        for (FreezeType freezeType : freezeTypes) {
            TransactionID txnId = TransactionID.newBuilder()
                    .accountID(nonAdminAccount)
                    .transactionValidStart(Timestamp.newBuilder().seconds(1000).build())
                    .build();
            TransactionBody txn = TransactionBody.newBuilder()
                    .transactionID(txnId)
                    .freeze(FreezeTransactionBody.newBuilder()
                            .freezeType(freezeType)
                            .startTime(Timestamp.newBuilder().seconds(2000).build())
                            .updateFile(fileId)
                            .fileHash(Bytes.wrap(new byte[48]))
                            .build())
                    .build();
            given(preHandleContext.body()).willReturn(txn);
            assertDoesNotThrow(() -> subject.preHandle(preHandleContext));

            given(handleContext.body()).willReturn(txn);
            assertDoesNotThrow(() -> subject.handle(handleContext));
        }
    }

    @Test
    void happyPathPrepareUpgrade() {
        // when using these freeze types, it is required to set an update file and file hash and they must match
        // start time not required

        FileID fileId = FileID.newBuilder().fileNum(1234L).build();
        given(specialFileStore.get(fileId)).willReturn(Optional.of(new byte[0]));
        TransactionID txnId = TransactionID.newBuilder()
                .accountID(nonAdminAccount)
                .transactionValidStart(Timestamp.newBuilder().seconds(1000).build())
                .build();
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(txnId)
                .freeze(FreezeTransactionBody.newBuilder()
                        .freezeType(PREPARE_UPGRADE)
                        .updateFile(fileId)
                        .fileHash(Bytes.wrap(new byte[48]))
                        .build())
                .build();
        given(preHandleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));

        given(handleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void happyPathFreezeOnly() {
        // for FREEZE_ONLY, start time is required and it must be in the future

        TransactionID txnId = TransactionID.newBuilder()
                .accountID(nonAdminAccount)
                .transactionValidStart(Timestamp.newBuilder().seconds(1000).build())
                .build();
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(txnId)
                .freeze(FreezeTransactionBody.newBuilder()
                        .freezeType(FREEZE_ONLY)
                        .startTime(Timestamp.newBuilder().seconds(2000).build())
                        .build())
                .build();
        given(preHandleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));

        given(handleContext.body()).willReturn(txn);
        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void rejectIfNoStartTimeSet() {
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder().build())
                // do not set freeze start time
                .build();
        given(preHandleContext.body()).willReturn(txn);
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_FREEZE_TRANSACTION_BODY);
    }

    @Test
    void rejectIfStartHourSet() {
        // start hour is not supported
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder().startHour(3).build())
                .build();
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_FREEZE_TRANSACTION_BODY);
    }

    @Test
    void rejectIfStartMinSet() {
        // start min is not supported
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder().startMin(31).build())
                .build();
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_FREEZE_TRANSACTION_BODY);
    }

    @Test
    void rejectIfEndHourSet() {
        // end hour is not supported
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder().endHour(3).build())
                .build();
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_FREEZE_TRANSACTION_BODY);
    }

    @Test
    void rejectIfEndMinSet() {
        // end min is not supported
        TransactionBody txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(nonAdminAccount)
                        .transactionValidStart(
                                Timestamp.newBuilder().seconds(1000).build()))
                .freeze(FreezeTransactionBody.newBuilder().endMin(16).build())
                .build();
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_FREEZE_TRANSACTION_BODY);
    }
}
