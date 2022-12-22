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
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoCreateSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(CryptoCreateSuite.class);

    public static final String ACCOUNT = "account";
    public static final String ANOTHER_ACCOUNT = "anotherAccount";
    public static final String ED_25519_KEY = "ed25519Alias";
    public static final String LAZY_CREATION_ENABLED = "lazyCreation.enabled";

    public static void main(String... args) {
        new CryptoCreateSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                createAnAccountEmptyThresholdKey(),
                createAnAccountEmptyKeyList(),
                createAnAccountEmptyNestedKey(),
                createAnAccountInvalidKeyList(),
                createAnAccountInvalidNestedKeyList(),
                createAnAccountInvalidThresholdKey(),
                createAnAccountInvalidNestedThresholdKey(),
                createAnAccountThresholdKeyWithInvalidThreshold(),
                createAnAccountInvalidED25519(),
                syntaxChecksAreAsExpected(),
                usdFeeAsExpected(),
                createAnAccountWithStakingFields(),
                createAnAccountWithECDSAAlias(),
                createAnAccountWithEVMAddress(),
                createAnAccountWithED25519Alias(),
                createAnAccountWithECKeyAndNoAlias(),
                createAnAccountWithEDKeyAndNoAlias(),
                createAnAccountWithED25519KeyAndED25519Alias(),
                createAnAccountWithECKeyAndECKeyAlias());
    }

    private HapiSpec createAnAccountWithStakingFields() {
        return defaultHapiSpec("createAnAccountWithStakingFields")
                .given(
                        cryptoCreate("civilianWORewardStakingNode")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(true)
                                .stakedNodeId(0),
                        getAccountInfo("civilianWORewardStakingNode")
                                .has(
                                        accountWith()
                                                .isDeclinedReward(true)
                                                .noStakedAccountId()
                                                .stakedNodeId(0)))
                .when(
                        cryptoCreate("civilianWORewardStakingAcc")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(true)
                                .stakedAccountId("0.0.10"),
                        getAccountInfo("civilianWORewardStakingAcc")
                                .has(
                                        accountWith()
                                                .isDeclinedReward(true)
                                                .noStakingNodeId()
                                                .stakedAccountId("0.0.10")))
                .then(
                        cryptoCreate("civilianWRewardStakingNode")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(false)
                                .stakedNodeId(0),
                        getAccountInfo("civilianWRewardStakingNode")
                                .has(
                                        accountWith()
                                                .isDeclinedReward(false)
                                                .noStakedAccountId()
                                                .stakedNodeId(0)),
                        cryptoCreate("civilianWRewardStakingAcc")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(false)
                                .stakedAccountId("0.0.10"),
                        getAccountInfo("civilianWRewardStakingAcc")
                                .has(
                                        accountWith()
                                                .isDeclinedReward(false)
                                                .noStakingNodeId()
                                                .stakedAccountId("0.0.10")),
                        /* --- sentiel values throw */
                        cryptoCreate("invalidStakedAccount")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(false)
                                .stakedAccountId("0.0.0")
                                .hasPrecheck(INVALID_STAKING_ID),
                        cryptoCreate("invalidStakedNode")
                                .balance(ONE_HUNDRED_HBARS)
                                .declinedReward(false)
                                .stakedNodeId(-1L)
                                .hasPrecheck(INVALID_STAKING_ID));
    }

    /* Prior to 0.13.0, a "canonical" CryptoCreate (one sig, 3 month auto-renew) cost 1¢. */
    private HapiSpec usdFeeAsExpected() {
        double preV13PriceUsd = 0.01;
        double v13PriceUsd = 0.05;
        double autoAssocSlotPrice = 0.0018;
        double v13PriceUsdOneAutoAssociation = v13PriceUsd + autoAssocSlotPrice;
        double v13PriceUsdTenAutoAssociations = v13PriceUsd + 10 * autoAssocSlotPrice;

        final var noAutoAssocSlots = "noAutoAssocSlots";
        final var oneAutoAssocSlot = "oneAutoAssocSlot";
        final var tenAutoAssocSlots = "tenAutoAssocSlots";
        final var token = "token";

        return defaultHapiSpec("usdFeeAsExpected")
                .given(
                        cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
                        getAccountBalance("civilian").hasTinyBars(ONE_HUNDRED_HBARS))
                .when(
                        tokenCreate(token).autoRenewPeriod(THREE_MONTHS_IN_SECONDS),
                        cryptoCreate("neverToBe")
                                .balance(0L)
                                .memo("")
                                .entityMemo("")
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .payingWith("civilian")
                                .feeUsd(preV13PriceUsd)
                                .hasPrecheck(INSUFFICIENT_TX_FEE),
                        getAccountBalance("civilian").hasTinyBars(ONE_HUNDRED_HBARS),
                        cryptoCreate("noAutoAssoc")
                                .key("civilian")
                                .balance(0L)
                                .via(noAutoAssocSlots)
                                .blankMemo()
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .signedBy("civilian")
                                .payingWith("civilian"),
                        cryptoCreate("oneAutoAssoc")
                                .key("civilian")
                                .balance(0L)
                                .maxAutomaticTokenAssociations(1)
                                .via(oneAutoAssocSlot)
                                .blankMemo()
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .signedBy("civilian")
                                .payingWith("civilian"),
                        cryptoCreate("tenAutoAssoc")
                                .key("civilian")
                                .balance(0L)
                                .maxAutomaticTokenAssociations(10)
                                .via(tenAutoAssocSlots)
                                .blankMemo()
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .signedBy("civilian")
                                .payingWith("civilian"),
                        getTxnRecord(tenAutoAssocSlots).logged())
                .then(
                        validateChargedUsd(noAutoAssocSlots, v13PriceUsd),
                        validateChargedUsd(oneAutoAssocSlot, v13PriceUsdOneAutoAssociation),
                        validateChargedUsd(tenAutoAssocSlots, v13PriceUsdTenAutoAssociations));
    }

    public HapiSpec syntaxChecksAreAsExpected() {
        return defaultHapiSpec("SyntaxChecksAreAsExpected")
                .given()
                .when()
                .then(
                        cryptoCreate("broken")
                                .autoRenewSecs(1L)
                                .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE),
                        cryptoCreate("alsoBroken")
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING));
    }

    private HapiSpec createAnAccountEmptyThresholdKey() {
        KeyShape shape = threshOf(0, 0);
        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountEmptyThresholdKey")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(KEY_REQUIRED));
    }

    private HapiSpec createAnAccountEmptyKeyList() {
        KeyShape shape = listOf(0);
        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountEmptyKeyList")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(KEY_REQUIRED));
    }

    private HapiSpec createAnAccountEmptyNestedKey() {
        KeyShape emptyThresholdShape = threshOf(0, 0);
        KeyShape emptyListShape = listOf(0);
        KeyShape shape = threshOf(2, emptyThresholdShape, emptyListShape);
        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountEmptyThresholdKey")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(KEY_REQUIRED));
    }

    // One of element in key list is not valid
    private HapiSpec createAnAccountInvalidKeyList() {
        KeyShape emptyThresholdShape = threshOf(0, 0);
        KeyShape shape = listOf(SIMPLE, SIMPLE, emptyThresholdShape);
        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountInvalidKeyList")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY));
    }

    // One of element in nested key list is not valid
    private HapiSpec createAnAccountInvalidNestedKeyList() {
        KeyShape invalidListShape = listOf(SIMPLE, SIMPLE, listOf(0));
        KeyShape shape = listOf(SIMPLE, SIMPLE, invalidListShape);
        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountInvalidNestedKeyList")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY));
    }

    // One of element in threshold key is not valid
    private HapiSpec createAnAccountInvalidThresholdKey() {
        KeyShape emptyListShape = listOf(0);
        KeyShape thresholdShape = threshOf(1, SIMPLE, SIMPLE, emptyListShape);
        long initialBalance = 10_000L;

        // build a threshold key with one of key is invalid
        Key randomKey1 =
                Key.newBuilder().setEd25519(ByteString.copyFrom(randomUtf8Bytes(32))).build();
        Key randomKey2 =
                Key.newBuilder().setEd25519(ByteString.copyFrom(randomUtf8Bytes(32))).build();
        Key shortKey = Key.newBuilder().setEd25519(ByteString.copyFrom(new byte[10])).build();

        KeyList invalidKeyList =
                KeyList.newBuilder()
                        .addKeys(randomKey1)
                        .addKeys(randomKey2)
                        .addKeys(shortKey)
                        .build();

        ThresholdKey invalidThresholdKey =
                ThresholdKey.newBuilder().setThreshold(2).setKeys(invalidKeyList).build();

        Key regKey1 = Key.newBuilder().setThresholdKey(invalidThresholdKey).build();
        Key regKey2 = Key.newBuilder().setKeyList(invalidKeyList).build();

        return defaultHapiSpec("createAnAccountInvalidThresholdKey")
                .given()
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    spec.registry().saveKey("regKey1", regKey1);
                                    spec.registry().saveKey("regKey2", regKey2);
                                }),
                        cryptoCreate("badThresholdKeyAccount")
                                .keyShape(thresholdShape)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY),
                        cryptoCreate("badThresholdKeyAccount2")
                                .key("regKey1")
                                .balance(initialBalance)
                                .logged()
                                .signedBy(GENESIS)
                                .hasPrecheck(INVALID_ADMIN_KEY),
                        cryptoCreate("badThresholdKeyAccount3")
                                .key("regKey2")
                                .balance(initialBalance)
                                .logged()
                                .signedBy(GENESIS)
                                .hasPrecheck(INVALID_ADMIN_KEY));
    }

    // createAnAccountInvalidNestedThresholdKey
    private HapiSpec createAnAccountInvalidNestedThresholdKey() {
        KeyShape goodShape = threshOf(2, 3);
        KeyShape thresholdShape0 = threshOf(0, SIMPLE, SIMPLE, SIMPLE);
        KeyShape thresholdShape4 = threshOf(4, SIMPLE, SIMPLE, SIMPLE);
        KeyShape badShape0 = threshOf(1, thresholdShape0, SIMPLE, SIMPLE);
        KeyShape badShape4 = threshOf(1, SIMPLE, thresholdShape4, SIMPLE);

        KeyShape shape0 = threshOf(3, badShape0, goodShape, goodShape, goodShape);
        KeyShape shape4 = threshOf(3, goodShape, badShape4, goodShape, goodShape);

        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountInvalidNestedKeyList")
                .given()
                .when()
                .then(
                        cryptoCreate("noKeys")
                                .keyShape(shape0)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY),
                        cryptoCreate("noKeys")
                                .keyShape(shape4)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY));
    }

    private HapiSpec createAnAccountThresholdKeyWithInvalidThreshold() {
        KeyShape thresholdShape0 = threshOf(0, SIMPLE, SIMPLE, SIMPLE);
        KeyShape thresholdShape4 = threshOf(4, SIMPLE, SIMPLE, SIMPLE);

        long initialBalance = 10_000L;

        return defaultHapiSpec("createAnAccountThresholdKeyWithInvalidThreshold")
                .given()
                .when()
                .then(
                        cryptoCreate("badThresholdKeyAccount1")
                                .keyShape(thresholdShape0)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY),
                        cryptoCreate("badThresholdKeyAccount2")
                                .keyShape(thresholdShape4)
                                .balance(initialBalance)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY));
    }

    private HapiSpec createAnAccountInvalidED25519() {
        long initialBalance = 10_000L;
        Key emptyKey = Key.newBuilder().setEd25519(ByteString.EMPTY).build();
        Key shortKey = Key.newBuilder().setEd25519(ByteString.copyFrom(new byte[10])).build();
        return defaultHapiSpec("createAnAccountInvalidED25519")
                .given()
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    spec.registry().saveKey("shortKey", shortKey);
                                    spec.registry().saveKey("emptyKey", emptyKey);
                                }),
                        cryptoCreate("shortKey")
                                .key("shortKey")
                                .balance(initialBalance)
                                .signedBy(GENESIS)
                                .logged()
                                .hasPrecheck(INVALID_ADMIN_KEY),
                        cryptoCreate("emptyKey")
                                .key("emptyKey")
                                .balance(initialBalance)
                                .signedBy(GENESIS)
                                .logged()
                                .hasPrecheck(BAD_ENCODING));
    }

    private HapiSpec createAnAccountWithECDSAAlias() {
        return defaultHapiSpec("CreateAnAccountWithECDSAAlias")
                .given(newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .balance(100 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);

                                    allRunFor(spec, op, op2);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(ecdsaKey)
                                                                    .alias(SECP_256K1_SOURCE_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo);
                                }))
                .then();
    }

    private HapiSpec createAnAccountWithEVMAddress() {
        return defaultHapiSpec("CreateAnAccountWithEVMAddress")
                .given(newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .evmAddress(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op3 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);

                                    allRunFor(spec, op, op2, op3, op4);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .logged()
                                                    .has(
                                                            accountWith()
                                                                    .evmAddress(evmAddressBytes)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo);
                                }))
                .then();
    }

    private HapiSpec createAnAccountWithED25519Alias() {
        return defaultHapiSpec("CreateAnAccountWithED25519Alias")
                .given(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var ed25519Key = spec.registry().getKey(ED_25519_KEY);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ed25519Key.toByteString())
                                                    .balance(1000 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ed25519Key.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY);

                                    allRunFor(spec, op, op2);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(ed25519Key)
                                                                    .alias(ED_25519_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo);
                                }))
                .then();
    }

    private HapiSpec createAnAccountWithECKeyAndNoAlias() {
        return defaultHapiSpec("CreateAnAccountWithECKeyAndNoAlias")
                .given(newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var op = cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op3 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op4 =
                                            cryptoCreate(ANOTHER_ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .balance(100 * ONE_HBAR);

                                    allRunFor(spec, op, op2, op3, op4);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .evmAddress(evmAddressBytes));
                                    var hapiGetAnotherAccountInfo =
                                            getAccountInfo(ANOTHER_ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .noAlias()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo, hapiGetAnotherAccountInfo);
                                }))
                .then();
    }

    private HapiSpec createAnAccountWithEDKeyAndNoAlias() {
        return defaultHapiSpec("CreateAnAccountWithEDKeyAndNoAlias")
                .given(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519))
                .when(cryptoCreate(ACCOUNT).key(ED_25519_KEY))
                .then(getAccountInfo(ACCOUNT).has(accountWith().key(ED_25519_KEY).noAlias()));
    }

    private HapiSpec createAnAccountWithED25519KeyAndED25519Alias() {
        return defaultHapiSpec("CreateAnAccountWithED25519KeyAndED25519Alias")
                .given(newKeyNamed(ED_25519_KEY).shape(KeyShape.ED25519))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var ed25519Key = spec.registry().getKey(ED_25519_KEY);
                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .key(ED_25519_KEY)
                                                    .alias(ed25519Key.toByteString())
                                                    .balance(1000 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ed25519Key.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY);

                                    allRunFor(spec, op, op2);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(ED_25519_KEY)
                                                                    .alias(ED_25519_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo);
                                }))
                .then();
    }

    private HapiSpec createAnAccountWithECKeyAndECKeyAlias() {
        return defaultHapiSpec("CreateAnAccountWithECKeyAndECKeyAlias")
                .given(newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);

                                    final var op =
                                            cryptoCreate(ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .alias(ecdsaKey.toByteString())
                                                    .balance(100 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ANOTHER_ACCOUNT)
                                                    .key(SECP_256K1_SOURCE_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op3 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);

                                    allRunFor(spec, op, op2, op3, op4);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .alias(SECP_256K1_SOURCE_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    var hapiGetAnotherAccountInfo =
                                            getAccountInfo(ANOTHER_ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(SECP_256K1_SOURCE_KEY)
                                                                    .noAlias()
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(spec, hapiGetAccountInfo, hapiGetAnotherAccountInfo);
                                }))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
