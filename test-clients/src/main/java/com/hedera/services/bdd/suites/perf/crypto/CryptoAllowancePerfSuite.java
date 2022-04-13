/*
 * -
 *  * ‌
 * * Hedera Services Node
 *  *
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  *
 * ​
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
 * ‍
 *
 */

package com.hedera.services.bdd.suites.perf.crypto;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;

public class CryptoAllowancePerfSuite extends LoadTest {
	private static final Logger log = LogManager.getLogger(CryptoAllowancePerfSuite.class);

	public static void main(String... args) {
		CryptoAllowancePerfSuite suite = new CryptoAllowancePerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runCryptoCreatesAndTokenCreates(),
				runCryptoAllowances()
		);
	}

	private HapiApiSpec runCryptoCreatesAndTokenCreates() {
		final int NUM_CREATES = 5000;
		return defaultHapiSpec("runCryptoCreatesAndTokenCreates")
				.given(
				).when(
						inParallel(
								asOpArray(NUM_CREATES, i ->
										(i == (NUM_CREATES - 1)) ?
												cryptoCreate("owner" + i)
														.balance(100_000_000_000L)
														.key(GENESIS)
														.withRecharging()
														.rechargeWindow(30)
														.payingWith(GENESIS) :
												cryptoCreate("owner" + i)
														.balance(100_000_000_000L)
														.key(GENESIS)
														.withRecharging()
														.rechargeWindow(30)
														.payingWith(GENESIS)
														.deferStatusResolution()
								)
						),
						inParallel(
								asOpArray(NUM_CREATES, i ->
										(i == (NUM_CREATES - 1)) ?
												cryptoCreate("spender" + i)
														.balance(100_000_000_000L)
														.key(GENESIS)
														.withRecharging()
														.rechargeWindow(30)
														.payingWith(GENESIS) :
												cryptoCreate("spender" + i)
														.balance(100_000_000_000L)
														.key(GENESIS)
														.withRecharging()
														.rechargeWindow(30)
														.payingWith(GENESIS)
														.deferStatusResolution()
								)
						)
				).then(
						inParallel(
								asOpArray(NUM_CREATES, i ->
										(i == (NUM_CREATES - 1)) ? tokenCreate("token" + i)
												.payingWith(GENESIS)
												.initialSupply(100_000_000_000L)
												.signedBy(GENESIS) :
												tokenCreate("token" + i)
														.payingWith(GENESIS)
														.signedBy(GENESIS)
														.initialSupply(100_000_000_000L)
														.deferStatusResolution()
								)
						)
				);
	}

	private HapiApiSpec runCryptoAllowances() {
		final int NUM_ALLOWANCES = 5000;
		return defaultHapiSpec("runCryptoAllowances")
				.given(
				).when(
						inParallel(
								asOpArray(NUM_ALLOWANCES, i ->
										(i == (NUM_ALLOWANCES - 1)) ?
												cryptoApproveAllowance()
														.payingWith("owner" + i)
														.addCryptoAllowance("owner" + i, "spender" + i, 1L)
														.addTokenAllowance("owner" + i, "token" + i, "spender" + i,
																1L) :
												cryptoApproveAllowance()
														.payingWith("owner" + i)
														.addCryptoAllowance("owner" + i, "spender" + i, 1L)
														.addTokenAllowance("owner" + i, "token" + i, "spender" + i,
																1L)
								)
						)
				).then();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
