package com.hedera.services.store;

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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

/**
 * Loads and saves token-related entities to and from the Swirlds state, hiding
 * the details of Merkle types from client code by providing an interface in
 * terms of model objects whose methods can perform validated business logic.
 * <p>
 * When loading an token, fails fast by throwing an {@link InvalidTransactionException}
 * if the token is not usable in normal business logic. There are three such
 * cases:
 * <ol>
 * <li>The token is missing.</li>
 * <li>The token is deleted.</li>
 * <li>The token is expired and pending removal.</li>
 * </ol>
 * Note that in the third case, there <i>is</i> one valid use of the token;
 * namely, in an update transaction whose only purpose is to manually renew
 * the expired token. Such update transactions must use a dedicated
 * expiry-extension service, which will be implemented before TokenUpdate.
 * <p>
 * When saving a token or token relationship, invites an injected
 * {@link TransactionRecordService} to inspect the entity for changes that
 * may need to be included in the record of the transaction.
 */
@Singleton
public class TypedTokenStore extends ReadOnlyTokenStore {
	private final SideEffectsTracker sideEffectsTracker;

	/* Only needed for interoperability with legacy HTS during refactor */
	private final LegacyTreasuryRemover delegate;
	private final LegacyTreasuryAdder addKnownTreasury;

	@Inject
	public TypedTokenStore(
			final AccountStore accountStore,
			final BackingStore<TokenID, MerkleToken> tokens,
			final BackingStore<NftId, MerkleUniqueToken> uniqueTokens,
			final BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels,
			final LegacyTreasuryAdder legacyStoreDelegate,
			final LegacyTreasuryRemover delegate,
			final SideEffectsTracker sideEffectsTracker
	) {
		super(accountStore, tokens, uniqueTokens, tokenRels);
		this.delegate = delegate;
		this.sideEffectsTracker = sideEffectsTracker;
		this.addKnownTreasury = legacyStoreDelegate;
	}

	/**
	 * Persists the given token relationships to the Swirlds state, inviting the injected
	 * {@link TransactionRecordService} to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord}
	 * of the active transaction with these changes.
	 *
	 * @param tokenRelationships
	 * 		the token relationships to save
	 */
	public void commitTokenRelationships(final List<TokenRelationship> tokenRelationships) {
		for (var tokenRelationship : tokenRelationships) {
			final var key = EntityNumPair.fromModelRel(tokenRelationship);
			if (tokenRelationship.isDestroyed()) {
				tokenRels.remove(Pair.of(
						tokenRelationship.getAccount().getId().asGrpcAccount(),
						tokenRelationship.getToken().getId().asGrpcToken()));
			} else {
				persistNonDestroyed(tokenRelationship, key);
			}
		}
		sideEffectsTracker.trackTokenBalanceChanges(tokenRelationships);
	}

	private void persistNonDestroyed(
			TokenRelationship modelRel,
			EntityNumPair key
	) {
		final var isNewRel = modelRel.isNotYetPersisted();
		final var mutableTokenRel = isNewRel
				? new MerkleTokenRelStatus()
				: tokenRels.getRef(key.asAccountTokenRel());
		mutableTokenRel.setBalance(modelRel.getBalance());
		mutableTokenRel.setFrozen(modelRel.isFrozen());
		mutableTokenRel.setKycGranted(modelRel.isKycGranted());
		mutableTokenRel.setAutomaticAssociation(modelRel.isAutomaticAssociation());
		mutableTokenRel.setKey(modelRel.getKey());
		mutableTokenRel.setNext(modelRel.getNextKey());
		mutableTokenRel.setPrev(modelRel.getPrevKey());
		tokenRels.put(key.asAccountTokenRel(), mutableTokenRel);
	}

	/**
	 * Invites the injected {@link TransactionRecordService} to include the changes to the exported transaction record
	 * Currently, the only implemented tracker is the {@link OwnershipTracker} which records the changes to the
	 * ownership
	 * of {@link UniqueToken}
	 *
	 * @param ownershipTracker
	 * 		holds changes to {@link UniqueToken} ownership
	 */
	public void commitTrackers(final OwnershipTracker ownershipTracker) {
		sideEffectsTracker.trackTokenOwnershipChanges(ownershipTracker);
	}

	/**
	 * Persists the given token to the Swirlds state, inviting the injected {@link TransactionRecordService}
	 * to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} of the active transaction
	 * with these changes.
	 *
	 * @param token
	 * 		the token to save
	 */
	public void commitToken(Token token) {
		final var mutableToken = tokens.getRef(token.getId().asGrpcToken());
		mapModelChanges(token, mutableToken);
		tokens.put(token.getId().asGrpcToken(), mutableToken);

		if (token.hasMintedUniqueTokens()) {
			persistMinted(token.mintedUniqueTokens());
		}
		if (token.hasRemovedUniqueTokens()) {
			destroyRemoved(token.removedUniqueTokens());
		}

		/* Only needed during HTS refactor. Will be removed once all operations that
		 * refer to the knownTreasuries in-memory structure are refactored */
		if (token.isDeleted()) {
			final AccountID affectedTreasury = token.getTreasury().getId().asGrpcAccount();
			final TokenID mutatedToken = token.getId().asGrpcToken();
			delegate.removeKnownTreasuryForToken(affectedTreasury, mutatedToken);
		}
		sideEffectsTracker.trackTokenChanges(token);
	}

	/**
	 * Instantiates a new {@link MerkleToken} based on the given new mutable {@link Token}.
	 * Maps the properties between the mutable and immutable token, and later puts the immutable one in state.
	 * Adds the token's treasury to the known treasuries map.
	 *
	 * @param token
	 * 		- the model of the token to be persisted in state.
	 */
	public void persistNew(Token token) {
		/* create new merkle token */
		final var newMerkleTokenId = EntityNum.fromLong(token.getId().num());
		final var newMerkleToken = new MerkleToken(
				token.getExpiry(),
				token.getTotalSupply(),
				token.getDecimals(),
				token.getSymbol(),
				token.getName(),
				token.isFrozenByDefault(),
				!token.hasKycKey(),
				token.getTreasury().getId().asEntityId()
		);

		/* map changes */
		mapModelChanges(token, newMerkleToken);
		tokens.put(newMerkleTokenId.toGrpcTokenId(), newMerkleToken);

		addKnownTreasury.perform(token.getTreasury().getId().asGrpcAccount(), token.getId().asGrpcToken());

		sideEffectsTracker.trackTokenChanges(token);
	}

	public void persistNft(UniqueToken nft) {
		final var tokenId = nft.getTokenId();
		final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), nft.getSerialNumber());
		final var mutableNft = uniqueTokens.getRef(nftId);
		mapModelChanges(nft, mutableNft);
		uniqueTokens.put(nftId, mutableNft);
	}

	private void destroyRemoved(List<UniqueToken> nfts) {
		for (final var nft : nfts) {
			uniqueTokens.remove(NftId.withDefaultShardRealm(nft.getTokenId().num(), nft.getSerialNumber()));
		}
	}

	private void persistMinted(List<UniqueToken> nfts) {
		for (final var nft : nfts) {
			final var merkleNft = new MerkleUniqueToken(MISSING_ENTITY_ID, nft.getMetadata(), nft.getCreationTime());
			uniqueTokens.put(NftId.withDefaultShardRealm(nft.getTokenId().num(), nft.getSerialNumber()), merkleNft);
		}
	}

	private void mapModelChanges(Token token, MerkleToken mutableToken) {
		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
			mutableToken.setAutoRenewPeriod(token.getAutoRenewPeriod());
		}
		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
		mutableToken.setAccountsFrozenByDefault(token.isFrozenByDefault());
		mutableToken.setLastUsedSerialNumber(token.getLastUsedSerialNumber());
		mutableToken.setTokenType(token.getType());
		mutableToken.setSupplyType(token.getSupplyType());
		mutableToken.setMemo(token.getMemo());
		mutableToken.setAdminKey(token.getAdminKey());
		mutableToken.setSupplyKey(token.getSupplyKey());
		mutableToken.setWipeKey(token.getWipeKey());
		mutableToken.setFreezeKey(token.getFreezeKey());
		mutableToken.setKycKey(token.getKycKey());
		mutableToken.setFeeScheduleKey(token.getFeeScheduleKey());
		mutableToken.setPauseKey(token.getPauseKey());
		mutableToken.setMaxSupply(token.getMaxSupply());
		mutableToken.setDeleted(token.isDeleted());
		mutableToken.setPaused(token.isPaused());

		if (token.getCustomFees() != null) {
			mutableToken.setFeeSchedule(token.getCustomFees());
		}

		mutableToken.setExpiry(token.getExpiry());
	}

	private void mapModelChanges(UniqueToken nft, MerkleUniqueToken mutableNft) {
		mutableNft.setOwner(nft.getOwner().asEntityId());
		mutableNft.setSpender(nft.getSpender().asEntityId());
		mutableNft.setMetadata(nft.getMetadata());
		final var creationTime = nft.getCreationTime();
		mutableNft.setPackedCreationTime(packedTime(creationTime.getSeconds(), creationTime.getNanos()));
		mutableNft.setPrev(nft.getPrev());
		mutableNft.setNext(nft.getNext());
	}

	public void updateNftLinkedList(
			final Account account, final Id targetTokenId, final List<Long> serialNumbers) {
		List<UniqueToken> touchedUniqueTokens = new ArrayList<>();
		for (long serialNum : serialNumbers) {
			final var nft = loadUniqueToken(targetTokenId, serialNum);
			final var currHeadNftNum = account.getHeadNftId();
			final var currHeadNftSerialNum = account.getHeadNftSerialNum();

			if (currHeadNftNum == targetTokenId.num() && currHeadNftSerialNum == serialNum) {
				final var nextNftId = nft.getNext();
				if (!nextNftId.equals(NftNumPair.MISSING_NFT_NUM_PAIR)) {
					final var nextNft = loadUniqueToken(
							Id.fromGrpcToken(nextNftId.tokenId()), nextNftId.serialNum());
					nextNft.setPrev(NftNumPair.MISSING_NFT_NUM_PAIR);
					touchedUniqueTokens.add(nextNft);
				}
				account.setHeadNftId(nextNftId.tokenNum());
				account.setHeadNftSerialNum(nextNftId.serialNum());
			} else {
				final var nextNftId = nft.getNext() ;
				final var prevNftId = nft.getPrev();

				if (!nextNftId.equals(NftNumPair.MISSING_NFT_NUM_PAIR)) {
					final var nextNft = loadUniqueToken(
							Id.fromGrpcToken(nextNftId.tokenId()), nextNftId.serialNum());
					nextNft.setPrev(prevNftId);
					touchedUniqueTokens.add(nextNft);
				}

				final var prevNft = loadUniqueToken(
						Id.fromGrpcToken(prevNftId.tokenId()), prevNftId.serialNum());
				prevNft.setNext(nextNftId);
				touchedUniqueTokens.add(prevNft);
			}
		}

		for (var nft : touchedUniqueTokens) {
			persistNft(nft);
		}
	}

	@FunctionalInterface
	public interface LegacyTreasuryAdder {
		void perform(final AccountID aId, final TokenID tId);
	}

	@FunctionalInterface
	public interface LegacyTreasuryRemover {
		void removeKnownTreasuryForToken(final AccountID aId, final TokenID tId);
	}
}
