package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;

public class HederaStackedWorldStateUpdater
		extends AbstractStackedLedgerUpdater<HederaMutableWorldState, Account>
		implements HederaWorldUpdater {

	private static CustomizerFactory customizerFactory = ContractCustomizer::fromSponsorContract;

	private final HederaMutableWorldState worldState;
	private final GlobalDynamicProperties dynamicProperties;

	private Gas sbhRefund = Gas.ZERO;
	private int numAllocatedIds = 0;
	private ContractID lastAllocatedId = null;
	private ContractCustomizer pendingCreationCustomizer = null;

	public HederaStackedWorldStateUpdater(
			final AbstractLedgerWorldUpdater<HederaMutableWorldState, Account> updater,
			final HederaMutableWorldState worldState,
			final WorldLedgers trackingLedgers,
			final GlobalDynamicProperties dynamicProperties
	) {
		super(updater, trackingLedgers);
		this.worldState = worldState;
		this.dynamicProperties = dynamicProperties;
	}

	public boolean hasMutableLedgers() {
		return trackingLedgers().areMutable();
	}

	public boolean contractIsTokenTreasury(final Address addressOrAlias) {
		final var address = aliases().resolveForEvm(addressOrAlias);
		final var accountId = accountIdFromEvmAddress(address);
		return (int) trackingAccounts().get(accountId, NUM_TREASURY_TITLES) > 0;
	}

	public boolean contractHasAnyBalance(final Address addressOrAlias) {
		final var address = aliases().resolveForEvm(addressOrAlias);
		final var accountId = accountIdFromEvmAddress(address);
		return (int) trackingAccounts().get(accountId, NUM_POSITIVE_BALANCES) > 0;
	}

	public boolean contractOwnsNfts(final Address addressOrAlias) {
		final var address = aliases().resolveForEvm(addressOrAlias);
		final var accountId = accountIdFromEvmAddress(address);
		return (long) trackingAccounts().get(accountId, NUM_NFTS_OWNED) > 0L;
	}

	public byte[] unaliased(final byte[] evmAddress) {
		final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
		if (!addressOrAlias.equals(trackingLedgers().canonicalAddress(addressOrAlias))) {
			throw new InvalidTransactionException(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS);
		}
		return aliases().resolveForEvm(Address.wrap(Bytes.wrap(evmAddress))).toArrayUnsafe();
	}

	/**
	 * Returns the underlying entity id of the last allocated EVM address.
	 *
	 * @return the id of the last allocated address
	 */
	public ContractID idOfLastNewAddress() {
		return lastAllocatedId;
	}

	/**
	 * Given an address in mirror or alias form, returns its alias form (if it has one). We use this to make
	 * the ADDRESS opcode prioritize CREATE2 addresses over mirror addresses.
	 *
	 * @param addressOrAlias
	 * 		a mirror or alias address
	 * @return the alias form of the address, if it exists
	 */
	public Address priorityAddress(final Address addressOrAlias) {
		return trackingLedgers().canonicalAddress(addressOrAlias);
	}

	public Address newAliasedContractAddress(final Address sponsor, final Address alias) {
		final var mirrorAddress = newContractAddress(sponsor);
		final var curAliases = aliases();
		// Only link the alias if it's not already in use, or if the target of the alleged link
		// doesn't actually exist. (In the first case, a CREATE2 that tries to re-use an existing
		// alias address is going to fail in short order; in the second case, the existing link
		// must have been created by an inline create2 that failed, but didn't revert us---we are
		// free to re-use this alias).
		if (!curAliases.isInUse(alias) || isMissingTarget(alias)) {
			curAliases.link(alias, mirrorAddress);
		}
		return mirrorAddress;
	}

	@Override
	public void countIdsAllocatedByStacked(final int n) {
		numAllocatedIds += n;
	}

	@Override
	public Address newContractAddress(final Address sponsorAddressOrAlias) {
		final var sponsor = aliases().resolveForEvm(sponsorAddressOrAlias);
		final var newAddress = worldState.newContractAddress(sponsor);
		numAllocatedIds++;
		final var sponsorId = accountIdFromEvmAddress(sponsor);
		pendingCreationCustomizer = customizerFactory.apply(sponsorId, trackingAccounts());
		lastAllocatedId = contractIdFromEvmAddress(newAddress);
		return newAddress;
	}

	@Override
	public ContractCustomizer customizerForPendingCreation() {
		// When the ContractCreationProcessor starts, it calls createAccount() on the updater for the spawned
		// CONTRACT_CREATION message; so actually the parent updater has the customization details
		return (pendingCreationCustomizer != null)
				? pendingCreationCustomizer
				: wrappedWorldView().customizerForPendingCreation();
	}

	@Override
	public Account get(final Address addressOrAlias) {
		final var address = aliases().resolveForEvm(addressOrAlias);
		if (isTokenRedirect(address)) {
			return new WorldStateTokenAccount(address);
		}
		return super.get(addressOrAlias);
	}

	@Override
	public EvmAccount getAccount(final Address addressOrAlias) {
		final var address = aliases().resolveForEvm(addressOrAlias);
		if (isTokenRedirect(address)) {
			final var proxyAccount = new WorldStateTokenAccount(address);
			final var newMutable = new UpdateTrackingLedgerAccount<>(proxyAccount, trackingAccounts());
			return new WrappedEvmAccount(newMutable);
		}
		return super.getAccount(address);
	}

	@Override
	public Gas getSbhRefund() {
		return sbhRefund;
	}

	@Override
	public void addSbhRefund(Gas refund) {
		sbhRefund = sbhRefund.plus(refund);
	}

	@Override
	public void revert() {
		super.revert();
		// Note that reclaiming entity ids here is only on a best-effort basis, since
		// if an inline CREATE or CREATE2 fails and our frame doesn't explicitly revert,
		// the entity id allocated in newContractAddress() will not _actually_ be used
		while (numAllocatedIds != 0) {
			worldState.reclaimContractId();
			numAllocatedIds--;
		}
		sbhRefund = Gas.ZERO;
	}

	@Override
	public void commit() {
		super.commit();
		final var wrappedUpdater = ((HederaWorldUpdater) wrappedWorldView());
		wrappedUpdater.addSbhRefund(sbhRefund);
		wrappedUpdater.countIdsAllocatedByStacked(numAllocatedIds);
		sbhRefund = Gas.ZERO;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public WorldUpdater updater() {
		return new HederaStackedWorldStateUpdater(
				(AbstractLedgerWorldUpdater) this,
				worldState, trackingLedgers().wrapped(), dynamicProperties);
	}

	// --- Internal helpers
	boolean isTokenRedirect(final Address address) {
		return dynamicProperties.isRedirectTokenCallsEnabled() && trackingLedgers().isTokenAddress(address);
	}

	private boolean isMissingTarget(final Address alias) {
		final var target = aliases().resolveForEvm(alias);
		return !trackingAccounts().exists(accountIdFromEvmAddress(target));
	}

	@FunctionalInterface
	interface CustomizerFactory {
		ContractCustomizer apply(AccountID id, TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger);
	}

	// --- Only used by unit tests
	@VisibleForTesting
	static void setCustomizerFactory(final CustomizerFactory customizerFactory) {
		HederaStackedWorldStateUpdater.customizerFactory = customizerFactory;
	}
}
