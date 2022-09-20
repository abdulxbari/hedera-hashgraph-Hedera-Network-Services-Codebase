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
package com.hedera.services.bdd.suites.streaming;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.verifyRecordStreams;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.verification.TransactionLogger;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordStreamValidation extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(RecordStreamValidation.class);

    private static final String PATH_TO_LOCAL_STREAMS = "../hedera-node/data/recordstreams";

    public static void main(String... args) {
        new RecordStreamValidation().runSuiteSync();
    }

    public static final String ACCOUNT_1 = "ACCOUNT_1";
    public static final String ACCOUNT_2 = "ACCOUNT_2";

    private TransactionLogger transactionLogger;

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        this.transactionLogger = new TransactionLogger();
        return List.of(
                new HapiApiSpec[] {
                    createSomeAccounts(),
                    //                    recordStreamSanityChecks(),
                });
    }

    private HapiApiSpec createSomeAccounts() {
        final var firstCreateTxn = "firstCreateTxn";
        final var secondCreateTxn = "secondCreateTxn";
        final var ACCOUNT_1_INIT_BALANCE = ONE_HUNDRED_HBARS;
        final var ACCOUNT_2_INIT_BALANCE = ONE_HUNDRED_HBARS / 2;
        final var firstXferTxn = "firstXferTxn";
        final var secondXferTxn = "secondXferTxn";
        final var thirdXferTxn = "thirdXferTxn";
        final var firstXferAmount = 50;
        final var supplyKey = "supplyKey";
        final String fungibleToken = "fungibleToken";
        final String nonFungibleToken = "nonFungibleToken";
        return defaultHapiSpec("RecordStreamSanityChecks")
                .given(
                        //            overriding("cache.records.ttl", "500"),
                        // Create ACCOUNT 1
                        newKeyNamed(supplyKey),
                        cryptoCreate(ACCOUNT_1)
                                .balance(ACCOUNT_1_INIT_BALANCE)
                                .key(supplyKey)
                                .maxAutomaticTokenAssociations(5)
                                .via(firstCreateTxn),
                        getTxnRecord(firstCreateTxn)
                                .hasHbarAmount(GENESIS, -ACCOUNT_1_INIT_BALANCE)
                                .hasHbarAmount(ACCOUNT_1, ACCOUNT_1_INIT_BALANCE),
                        getAccountBalance(ACCOUNT_1).hasTinyBars(ACCOUNT_1_INIT_BALANCE),
                        // Create ACCOUNT 2
                        cryptoCreate(ACCOUNT_2)
                                .balance(ACCOUNT_2_INIT_BALANCE)
                                .maxAutomaticTokenAssociations(5)
                                .via(secondCreateTxn),
                        getTxnRecord(secondCreateTxn)
                                .hasHbarAmount(GENESIS, -(ACCOUNT_2_INIT_BALANCE))
                                .hasHbarAmount(ACCOUNT_2, ACCOUNT_2_INIT_BALANCE),
                        getAccountBalance(ACCOUNT_2).hasTinyBars(ACCOUNT_2_INIT_BALANCE),
                        tokenCreate(fungibleToken)
                                .initialSupply(50)
                                .treasury(ACCOUNT_2)
                                .tokenType(TokenType.FUNGIBLE_COMMON),
                        tokenCreate(nonFungibleToken)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyKey(supplyKey)
                                .treasury(ACCOUNT_1),
                        mintToken(
                                nonFungibleToken,
                                List.of(ByteStringUtils.wrapUnsafely("metadata1".getBytes()))))
                .when(
                        // ACCOUNT1 ----50 TinyBars---> ACCOUNT2
                        cryptoTransfer(
                                        TokenMovement.movingHbar(firstXferAmount)
                                                .between(ACCOUNT_1, ACCOUNT_2))
                                .via(firstXferTxn),
                        getAccountBalance(ACCOUNT_1)
                                .hasTinyBars(ACCOUNT_1_INIT_BALANCE - firstXferAmount),
                        getAccountBalance(ACCOUNT_2)
                                .hasTinyBars(ACCOUNT_2_INIT_BALANCE + firstXferAmount),
                        getTxnRecord(firstXferTxn)
                                .hasHbarAmount(ACCOUNT_1, -firstXferAmount)
                                .hasHbarAmount(ACCOUNT_2, +firstXferAmount),
                        // ACCOUNT2 ---- 10 FUNGIBLE TOKEN --> ACCOUNT 1
                        cryptoTransfer(
                                        TokenMovement.moving(10, fungibleToken)
                                                .between(ACCOUNT_2, ACCOUNT_1))
                                .via(secondXferTxn),
                        getAccountBalance(ACCOUNT_1).hasTokenBalance(fungibleToken, 10),
                        getAccountBalance(ACCOUNT_2).hasTokenBalance(fungibleToken, 40),
                        getTxnRecord(secondXferTxn)
                                .hasTokenAmount(fungibleToken, ACCOUNT_1, 10)
                                .hasTokenAmount(fungibleToken, ACCOUNT_2, -10),
                        // ACCOUNT1 ---- 1 NON-FUNGIBLE TOKEN --> ACCOUNT 2
                        cryptoTransfer(
                                        TokenMovement.movingUnique(nonFungibleToken, 1L)
                                                .between(ACCOUNT_1, ACCOUNT_2))
                                .via(thirdXferTxn),
                        getAccountBalance(ACCOUNT_1).hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(ACCOUNT_2).hasTokenBalance(nonFungibleToken, 1),
                        getTxnRecord(thirdXferTxn)
                                .hasNftTransfer(nonFungibleToken, ACCOUNT_1, ACCOUNT_2, 1L))
                .then()
                .verifyingRecordStream(transactionLogger);
    }

    private HapiApiSpec recordStreamSanityChecks() {
        AtomicReference<String> pathToStreams = new AtomicReference<>(PATH_TO_LOCAL_STREAMS);

        return defaultHapiSpec("RecordStreamSanityChecks")
                .given(
                        withOpContext(
                                (spec, opLog) -> {
                                    HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
                                    if (ciProps.has("recordStreamsDir")) {
                                        pathToStreams.set(ciProps.get("recordStreamsDir"));
                                    }
                                }))
                .when(cryptoCreate("triggerNewLogPeriodPlease").delayBy(2000))
                .then(verifyRecordStreams(pathToStreams::get, transactionLogger));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
