package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
 */

import com.hedera.services.test.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hederahashgraph.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoApproveAllowanceMetaTest {
	private final AccountID proxy = asAccount("0.0.1234");
	private CryptoAllowance cryptoAllowances = CryptoAllowance.newBuilder().setSpender(proxy).setAmount(10L).build();
	private TokenAllowance tokenAllowances = TokenAllowance.newBuilder()
			.setSpender(proxy).setAmount(10L).setTokenId(IdUtils.asToken("0.0.1000")).build();
	private NftAllowance nftAllowances = NftAllowance.newBuilder().setSpender(proxy)
			.setTokenId(IdUtils.asToken("0.0.1000"))
			.addAllSerialNumbers(List.of(1L, 2L, 3L)).build();

	@Test
	void allGettersAndToStringWork() {
		final var expected = "CryptoAllowanceMeta{numOfCryptoAllowances=1, numOfTokenAllowances=2, " +
				"numOfNftAllowances=3, aggregatedNftAllowancesWithSerials=10, effectiveNow=1234567, " +
				"msgBytesUsed=112}";
		final var now = 1_234_567;
		final var subject = CryptoAllowanceMeta.newBuilder()
				.numOfCryptoAllowances(1)
				.numOfTokenAllowances(2)
				.numOfNftAllowances(3)
				.msgBytesUsed(112)
				.aggregatedNftAllowancesWithSerials(10)
				.effectiveNow(now)
				.build();

		assertEquals(now, subject.getEffectiveNow());
		assertEquals(10, subject.getAggregatedNftAllowancesWithSerials());
		assertEquals(1, subject.getNumOfCryptoAllowances());
		assertEquals(2, subject.getNumOfTokenAllowances());
		assertEquals(3, subject.getNumOfNftAllowances());
		assertEquals(112, subject.getMsgBytesUsed());
		assertEquals(expected, subject.toString());
	}

	@Test
	void calculatesBaseSizeAsExpected() {
		final var cryptoApproveTxnBody = CryptoApproveAllowanceTransactionBody
				.newBuilder()
				.addAllCryptoAllowances(List.of(cryptoAllowances))
				.addAllTokenAllowances(List.of(tokenAllowances))
				.addAllNftAllowances(List.of(nftAllowances))
				.build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setCryptoApproveAllowance(
						cryptoApproveTxnBody
				).build();

		var subject = new CryptoAllowanceMeta(cryptoApproveTxnBody,
				canonicalTxn.getTransactionID().getTransactionValidStart().getSeconds());

		final var expectedMsgBytes = CRYPTO_ALLOWANCE_SIZE
				+ TOKEN_ALLOWANCE_SIZE
				+ NFT_ALLOWANCE_SIZE;

		assertEquals(expectedMsgBytes, subject.getMsgBytesUsed());
		assertEquals(1, subject.getNumOfCryptoAllowances());
		assertEquals(1, subject.getNumOfTokenAllowances());
		assertEquals(1, subject.getNumOfNftAllowances());
		assertEquals(3, subject.getAggregatedNftAllowancesWithSerials());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var now = 1_234_567;
		final var subject1 = CryptoAllowanceMeta.newBuilder()
				.numOfCryptoAllowances(1)
				.numOfTokenAllowances(2)
				.numOfNftAllowances(3)
				.aggregatedNftAllowancesWithSerials(10)
				.effectiveNow(now)
				.build();

		final var subject2 = CryptoAllowanceMeta.newBuilder()
				.numOfCryptoAllowances(1)
				.numOfTokenAllowances(2)
				.numOfNftAllowances(3)
				.aggregatedNftAllowancesWithSerials(10)
				.effectiveNow(now)
				.build();

		assertEquals(subject1, subject2);
		assertEquals(subject1.hashCode(), subject2.hashCode());
	}
}
