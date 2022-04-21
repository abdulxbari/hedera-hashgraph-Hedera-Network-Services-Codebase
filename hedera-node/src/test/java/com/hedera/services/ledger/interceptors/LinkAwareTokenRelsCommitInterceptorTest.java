package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LinkAwareTokenRelsCommitInterceptorTest {
	@Mock
	private TxnAccessor accessor;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private TokenRelsLinkManager relsLinkManager;

	private LinkAwareTokenRelsCommitInterceptor subject;

	@BeforeEach
	void setUp() {
		subject = new LinkAwareTokenRelsCommitInterceptor(txnCtx, sideEffectsTracker, relsLinkManager);
	}

	@Test
	void noChangesAreNoop() {
		final var changes = new EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty>();

		subject.preview(changes);

		verifyNoInteractions(sideEffectsTracker);
	}

	@Test
	void tracksNothingIfOpIsNotAutoAssociating() {
		given(accessor.getFunction()).willReturn(CryptoUpdate);
		given(txnCtx.accessor()).willReturn(accessor);

		final var changes = someChanges();

		subject.preview(changes);

		verifyNoInteractions(sideEffectsTracker);
	}

	@Test
	void tracksSideEffectsIfOpIsAutoAssociating() {
		given(accessor.getFunction()).willReturn(TokenCreate);
		given(txnCtx.accessor()).willReturn(accessor);

		final var changes = someChanges();

		subject.preview(changes);

		verify(sideEffectsTracker).trackAutoAssociation(newAssocTokenId, aAccountId);
	}

	@Test
	void addsAndRemovesRelsAsExpected() {
		given(accessor.getFunction()).willReturn(ContractCall);
		given(txnCtx.accessor()).willReturn(accessor);

		final var expectedNewRel = new MerkleTokenRelStatus();
		expectedNewRel.setKey(EntityNumPair.fromLongs(aAccountId.getAccountNum(), newAssocTokenId.getTokenNum()));

		subject.preview(someChanges());

		verify(relsLinkManager).updateLinks(accountNum, List.of(tbdTokenNum), List.of(expectedNewRel));
	}

	private EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty> someChanges() {
		final var changes = new EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty>();
		changes.include(Pair.of(aAccountId, alreadyAssocTokenId), extantRel, Map.of());
		changes.include(Pair.of(aAccountId, newAssocTokenId), null, Map.of());
		tbdExtantRel.setKey(EntityNumPair.fromLongs(aAccountId.getAccountNum(), tbdAssocTokenId.getTokenNum()));
		changes.include(Pair.of(aAccountId, newAssocTokenId), tbdExtantRel, null);
		return changes;
	}

	final EntityNum accountNum = EntityNum.fromLong(1234);
	final AccountID aAccountId = accountNum.toGrpcAccountId();
	final TokenID alreadyAssocTokenId = TokenID.newBuilder().setTokenNum(1235).build();
	final TokenID newAssocTokenId = TokenID.newBuilder().setTokenNum(1236).build();
	final EntityNum tbdTokenNum = EntityNum.fromLong(1237);
	final TokenID tbdAssocTokenId = tbdTokenNum.toGrpcTokenId();
	final MerkleTokenRelStatus extantRel = new MerkleTokenRelStatus();
	final MerkleTokenRelStatus tbdExtantRel = new MerkleTokenRelStatus();
}