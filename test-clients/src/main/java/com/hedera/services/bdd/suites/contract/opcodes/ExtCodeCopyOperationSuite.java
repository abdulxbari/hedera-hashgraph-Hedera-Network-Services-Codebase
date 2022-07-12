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
package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ExtCodeCopyOperationSuite extends HapiApiSuite {
    private static final Logger LOG = LogManager.getLogger(ExtCodeCopyOperationSuite.class);

    public static void main(String[] args) {
        new ExtCodeCopyOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(verifiesExistence());
    }

    @SuppressWarnings("java:S5960")
    HapiApiSpec verifiesExistence() {
        final var contract = "ExtCodeOperationsChecker";
        final var invalidAddress = "0x0000000000000000000000000000000000123456";
        final var emptyBytecode = ByteString.EMPTY;
        final var codeCopyOf = "codeCopyOf";

        return defaultHapiSpec("VerifiesExistence")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, codeCopyOf, invalidAddress)
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        contractCallLocal(contract, codeCopyOf, invalidAddress)
                                .hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var accountID =
                                            spec.registry().getAccountID(DEFAULT_PAYER);
                                    final var contractID = spec.registry().getContractId(contract);
                                    final var accountSolidityAddress =
                                            asHexedSolidityAddress(accountID);
                                    final var contractAddress = asHexedSolidityAddress(contractID);

                                    final var call =
                                            contractCall(
                                                            contract,
                                                            codeCopyOf,
                                                            accountSolidityAddress)
                                                    .via("callRecord");
                                    final var callRecord = getTxnRecord("callRecord");

                                    final var accountCodeCallLocal =
                                            contractCallLocal(
                                                            contract,
                                                            codeCopyOf,
                                                            accountSolidityAddress)
                                                    .saveResultTo("accountCode");

                                    final var contractCodeCallLocal =
                                            contractCallLocal(contract, codeCopyOf, contractAddress)
                                                    .saveResultTo("contractCode");

                                    final var getBytecodeCall =
                                            getContractBytecode(contract)
                                                    .saveResultTo("contractGetBytecode");

                                    allRunFor(
                                            spec,
                                            call,
                                            callRecord,
                                            accountCodeCallLocal,
                                            contractCodeCallLocal,
                                            getBytecodeCall);

                                    final var recordResult =
                                            callRecord.getResponseRecord().getContractCallResult();
                                    final var accountCode = spec.registry().getBytes("accountCode");
                                    final var contractCode =
                                            spec.registry().getBytes("contractCode");
                                    final var getBytecode =
                                            spec.registry().getBytes("contractGetBytecode");

                                    Assertions.assertEquals(
                                            emptyBytecode, recordResult.getContractCallResult());
                                    Assertions.assertArrayEquals(
                                            emptyBytecode.toByteArray(), accountCode);
                                    Assertions.assertArrayEquals(getBytecode, contractCode);
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
