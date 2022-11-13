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
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.onlyDefaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;

import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileCreateSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(FileCreateSuite.class);

    private static final long defaultMaxLifetime =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    public static void main(String... args) {
        new FileCreateSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    createWithMemoWorks(),
                    createFailsWithMissingSigs(),
                    createFailsWithPayerAccountNotFound(),
                    createFailsWithExcessiveLifetime(),
                    createWithAutoRenewWorks(),
                    updateWithAutoRenewWorks(),
                });
    }

    private HapiApiSpec createFailsWithExcessiveLifetime() {
        return defaultHapiSpec("CreateFailsWithExcessiveLifetime")
                .given()
                .when()
                .then(
                        fileCreate("test")
                                .lifetime(defaultMaxLifetime + 12_345L)
                                .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    private HapiApiSpec createWithMemoWorks() {
        String memo = "Really quite something!";

        return defaultHapiSpec("createWithMemoWorks")
                .given(
                        fileCreate("ntb")
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        fileCreate("memorable").entityMemo(memo))
                .when()
                .then(getFileInfo("memorable").hasExpectedLedgerId("0x03").hasMemo(memo));
    }

    private HapiApiSpec createWithAutoRenewWorks() {
        final var explicitExpiry = Instant.now()
                .plusSeconds(2 * ONE_MONTHS_IN_SECONDS)
                .getEpochSecond();
        final var someAutoRenewPeriod = THREE_MONTHS_IN_SECONDS + 1;

        final var withIgnoredExpiry = "withIgnoredExpiry";
        final var withUnspecifiedExpiry = "withUnspecifiedExpiry";
        final var withIgnoredAutoRenewPeriod = "withIgnoredAutoRenewPeriod";

        return onlyDefaultHapiSpec("CreateWithAutoRenewWorks")
                .given(
                        cryptoCreate(CIVILIAN),
                        // Auto-renew account must sign
                        fileCreate("missingAutoRenewSig")
                                .unmodifiable()
                                .autoRenewAccount(CIVILIAN)
                                .signedBy(DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileCreate("missingExpiryAndNoAutoRenewPeriod")
                                .unmodifiable()
                                .autoRenewAccount(CIVILIAN)
                                .noExplicitExpiry()
                                .hasKnownStatus(INVALID_EXPIRATION_TIME))
                .when(
                        fileCreate(withIgnoredExpiry)
                                .unmodifiable()
                                .expiry(explicitExpiry)
                                .autoRenewPeriod(someAutoRenewPeriod)
                                .autoRenewAccount(CIVILIAN),
                        fileCreate(withUnspecifiedExpiry)
                                .unmodifiable()
                                .noExplicitExpiry()
                                .autoRenewPeriod(someAutoRenewPeriod)
                                .autoRenewAccount(CIVILIAN),
                        fileCreate(withIgnoredAutoRenewPeriod)
                                .unmodifiable()
                                .expiry(explicitExpiry)
                                .autoRenewPeriod(someAutoRenewPeriod))
                .then(
                        getFileInfo(withIgnoredExpiry)
                                .hasAutoRenewPeriod(someAutoRenewPeriod)
                                .hasAutoRenewAccount(CIVILIAN),
                        getFileInfo(withUnspecifiedExpiry)
                                .hasAutoRenewPeriod(someAutoRenewPeriod)
                                .hasAutoRenewAccount(CIVILIAN),
                        getFileInfo(withIgnoredAutoRenewPeriod)
                                .hasAutoRenewPeriod(someAutoRenewPeriod)
                                .hasExpiry(() -> explicitExpiry));
    }

    private HapiApiSpec updateWithAutoRenewWorks() {
        final var target = "someFile";
        final var replAutoRenew = "replAutoRenew";
        final var firstAutoRenewPeriod = 7776666L;
        final var secondAutoRenewPeriod = 7777777L;
        return defaultHapiSpec("UpdateWithAutoRenewWorks")
                .given(
                        cryptoCreate(CIVILIAN),
                        cryptoCreate(replAutoRenew),
                        fileCreate(target)
                                .autoRenewPeriod(firstAutoRenewPeriod)
                                .autoRenewAccount(CIVILIAN))
                .when(
                        fileUpdate(target)
                                .autoRenewAccount(replAutoRenew)
                                .autoRenewPeriod(secondAutoRenewPeriod)
                                .signedBy(DEFAULT_PAYER, target)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileUpdate(target)
                                .autoRenewPeriod(secondAutoRenewPeriod)
                                .autoRenewAccount(replAutoRenew),
                        getFileInfo(target)
                                .hasAutoRenewPeriod(secondAutoRenewPeriod)
                                .hasAutoRenewAccount(replAutoRenew))
                .then(
                        fileUpdate(target)
                                .autoRenewAccount("0.0.0")
                                .signedBy(DEFAULT_PAYER, target),
                        getFileInfo(target).hasNoAutoRenewAccount());
    }

    private HapiApiSpec createFailsWithMissingSigs() {
        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
        SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

        return defaultHapiSpec("CreateFailsWithMissingSigs")
                .given()
                .when()
                .then(
                        fileCreate("test")
                                .waclShape(shape)
                                .sigControl(forKey("test", invalidSig))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileCreate("test").waclShape(shape).sigControl(forKey("test", validSig)));
    }

    private static Transaction replaceTxnNodeAccount(Transaction txn) {
        AccountID badNodeAccount =
                AccountID.newBuilder().setAccountNum(2000).setRealmNum(0).setShardNum(0).build();
        return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
    }

    private HapiApiSpec createFailsWithPayerAccountNotFound() {
        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

        return defaultHapiSpec("CreateFailsWithPayerAccountNotFound")
                .given()
                .when()
                .then(
                        fileCreate("test")
                                .withProtoStructure(HapiSpecSetup.TxnProtoStructure.OLD)
                                .waclShape(shape)
                                .sigControl(forKey("test", validSig))
                                .scrambleTxnBody(FileCreateSuite::replaceTxnNodeAccount)
                                .hasPrecheckFrom(INVALID_NODE_ACCOUNT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
