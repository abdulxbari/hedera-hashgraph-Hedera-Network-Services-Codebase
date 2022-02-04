/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.txns.crypto;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoAdjustAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final SigImpactHistorian sigImpactHistorian;
	private final AccountStore accountStore;
	private final AdjustAllowanceChecks adjustAllowanceChecks;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public CryptoAdjustAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final SigImpactHistorian sigImpactHistorian,
			final AccountStore accountStore,
			final AdjustAllowanceChecks allowanceChecks,
			final GlobalDynamicProperties dynamicProperties) {
		this.txnCtx = txnCtx;
		this.sigImpactHistorian = sigImpactHistorian;
		this.accountStore = accountStore;
		this.adjustAllowanceChecks = allowanceChecks;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoAdjustAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID owner = cryptoAdjustAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		/* --- Use models --- */
		final Id ownerId = Id.fromGrpcAccount(owner);
		final var ownerAccount = accountStore.loadAccount(ownerId);

		/* --- Do the business logic --- */
		adjustCryptoAllowances(op.getCryptoAllowancesList(), ownerAccount);
		adjustFungibleTokenAllowances(op.getTokenAllowancesList(), ownerAccount);
		adjustNftAllowances(op.getNftAllowancesList(), ownerAccount);

		/* --- validate --- */
		validateFalse(exceedsAccountLimit(ownerAccount), MAX_ALLOWANCES_EXCEEDED);

		/* --- Persist the owner account --- */
		accountStore.commitAccount(ownerAccount);
		sigImpactHistorian.markEntityChanged(ownerId.num());

		txnCtx.setStatus(SUCCESS);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoAdjustAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
		final AccountID owner = cryptoAllowanceTxn.getTransactionID().getAccountID();
		final var ownerAccount = accountStore.loadAccount(Id.fromGrpcAccount(owner));
		return adjustAllowanceChecks.allowancesValidation(cryptoAllowanceTxn, ownerAccount);
	}

	/**
	 * Adjust allowances based on all changes needed for Crypto allowances from the transaction.
	 * If the spender doesn't exist in the allowances map, the cryptoAdjustAllowance transaction
	 * acts as approval for the allowance and inserts allowance to the map. If the aggregated allowance becomes zero
	 * after adding the amount to the existing spender's allowance, the spender's entry will be removed from the Map.
	 * reduced.
	 *
	 * @param cryptoAllowances
	 * @param ownerAccount
	 */
	private void adjustCryptoAllowances(final List<CryptoAllowance> cryptoAllowances, final Account ownerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}
		Map<EntityNum, Long> cryptoMap = new TreeMap<>(ownerAccount.getCryptoAllowances());
		for (final var allowance : cryptoAllowances) {
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var amount = allowance.getAmount();

			// if there is no key in the map, adjust transaction acts as approve transaction
			if (!cryptoMap.containsKey(spender.asEntityNum())) {
				if (amount == 0) {
					continue;
				}
				cryptoMap.put(spender.asEntityNum(), amount);
				return;
			}

			final var existingAmount = cryptoMap.get(spender.asEntityNum());
			final var aggregatedAmount = existingAmount + amount;
			if (aggregatedAmount == 0) {
				cryptoMap.remove(spender.asEntityNum());
				continue;
			}
			cryptoMap.put(spender.asEntityNum(), aggregatedAmount);
		}
		ownerAccount.setCryptoAllowances(cryptoMap);
	}

	/**
	 * Applies all changes needed for NFT allowances from the transaction
	 *
	 * @param nftAllowances
	 * @param ownerAccount
	 */
	private void adjustNftAllowances(final List<NftAllowance> nftAllowances, final Account ownerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowancesMap = new TreeMap<>(ownerAccount.getNftAllowances());
		for (var allowance : nftAllowances) {
			final var spenderAccount = allowance.getSpender();
			final var approvedForAll = allowance.getApprovedForAll();
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = allowance.getTokenId();
			final var spender = Id.fromGrpcAccount(spenderAccount);

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			final FcTokenAllowance value;
			if (!nftAllowancesMap.containsKey(key)) {
				value = approvedForAll.getValue() ? FcTokenAllowance.from(
						approvedForAll.getValue()) : FcTokenAllowance.from(serialNums);
				nftAllowancesMap.put(key, value);
				return;
			}

			final var oldValue = nftAllowancesMap.get(key);
			if (approvedForAll.getValue()) {
				value = FcTokenAllowance.from(approvedForAll.getValue());
			} else {
				List<Long> serials = oldValue.getSerialNumbers();
				for (var serial : serialNums) {
					if (serial < 0) {
						serials.remove(serial);
					} else {
						serials.add(serial);
					}
				}
				value = FcTokenAllowance.from(serials);
			}
			nftAllowancesMap.put(key, value);
		}
		ownerAccount.setNftAllowances(nftAllowancesMap);
	}

	/**
	 * Applies all changes needed for fungible token allowances from the transaction
	 *
	 * @param tokenAllowances
	 * @param ownerAccount
	 */
	private void adjustFungibleTokenAllowances(final List<TokenAllowance> tokenAllowances,
			final Account ownerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}
		Map<FcTokenAllowanceId, Long> tokenAllowancesMap = new TreeMap<>(ownerAccount.getFungibleTokenAllowances());
		for (var allowance : tokenAllowances) {
			final var spenderAccount = allowance.getSpender();
			final var spender = Id.fromGrpcAccount(spenderAccount);
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			if (!tokenAllowancesMap.containsKey(key)) {
				if (amount == 0) {
					continue;
				}
				tokenAllowancesMap.put(key, amount);
			}
			final var oldAmount = tokenAllowancesMap.get(key);
			final var aggregatedAmount = oldAmount + amount;
			if (aggregatedAmount == 0) {
				tokenAllowancesMap.remove(key);
			} else {
				tokenAllowancesMap.put(key, aggregatedAmount);
			}
		}
		ownerAccount.setFungibleTokenAllowances(tokenAllowancesMap);
	}

	/**
	 * Checks if the total allowances of an account will exceed the limit after applying this transaction
	 *
	 * @param ownerAccount
	 * @return
	 */
	private boolean exceedsAccountLimit(final Account ownerAccount) {
		return ownerAccount.getTotalAllowances() > dynamicProperties.maxAllowanceLimitPerAccount();
	}
}
