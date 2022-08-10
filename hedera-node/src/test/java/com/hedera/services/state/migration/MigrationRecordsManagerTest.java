/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.state.initialization.TreasuryClonerTest.accountWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationRecordsManagerTest {
    private static final long fundingExpiry = 33197904000L;
    private static final long stakingRewardAccount = 800;
    private static final long nodeRewardAccount = 801;
    private static final ExpirableTxnRecord.Builder pretend200 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend201 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend275 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend276 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend277 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend800 = ExpirableTxnRecord.newBuilder();
    private static final ExpirableTxnRecord.Builder pretend801 = ExpirableTxnRecord.newBuilder();
    private static final Instant now = Instant.ofEpochSecond(1_234_567L);
    private static final Key EXPECTED_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    private static final String MEMO = "Release 0.24.1 migration record";
    private static final long contract1Expiry = 2000000L;
    private static final long contract2Expiry = 4000000L;
    private static final EntityId contract1Id = EntityId.fromIdentityCode(1);
    private static final EntityId contract2Id = EntityId.fromIdentityCode(2);

    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ConsensusTimeTracker consensusTimeTracker;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private SideEffectsTracker tracker800;
    @Mock private SideEffectsTracker tracker801;
    @Mock private SideEffectsTracker tracker200;
    @Mock private SideEffectsTracker tracker201;
    @Mock private SideEffectsTracker tracker275;
    @Mock private SideEffectsTracker tracker276;
    @Mock private SideEffectsTracker tracker277;
    @Mock private EntityCreator creator;
    @Mock private AccountNumbers accountNumbers;
    @Mock private TreasuryCloner treasuryCloner;
    @Mock private MerkleAccount merkleAccount;

    private final List<MerkleAccount> treasuryClones = new ArrayList<>();
    private final List<MerkleAccount> skippedCandidateClones = new ArrayList<>();
    private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();

    private final AtomicInteger nextTracker = new AtomicInteger();
    private final SyntheticTxnFactory factory = new SyntheticTxnFactory(dynamicProperties);

    private MigrationRecordsManager subject;

    @BeforeEach
    void setUp() {
        accounts.put(EntityNum.fromLong(1L), merkleAccount);
        accounts.put(EntityNum.fromLong(2L), merkleAccount);

        subject =
                new MigrationRecordsManager(
                        creator,
                        treasuryCloner,
                        sigImpactHistorian,
                        recordsHistorian,
                        () -> networkCtx,
                        consensusTimeTracker,
                        () -> accounts,
                        factory,
                        accountNumbers);

        subject.setSideEffectsFactory(
                () ->
                        switch (nextTracker.getAndIncrement()) {
                            case 0 -> tracker800;
                            case 1 -> tracker801;
                            case 2 -> tracker200;
                            case 3 -> tracker201;
                            case 4 -> tracker275;
                            case 5 -> tracker276;
                            default -> tracker277;
                        });
    }

    @Test
    void ifContextIndicatesRecordsNeedToBeStreamedThenDoesSo() {
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final var rewardSynthBody = expectedSyntheticRewardAccount();
        final var cloneSynthBody = expectedSyntheticTreasuryClone();
        final var nodeStakingBody = expectedExistingStakedTo(275L, pretendNodeStakedId);
        final var accountStakingBody = expectedExistingStakedTo(276L, pretendStakedAccountId);
        final var notStakingBody = expectedExistingStakedTo(277L, 0);

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker800, MEMO))
                .willReturn(pretend800);
        given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker801, MEMO))
                .willReturn(pretend801);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES,
                                tracker200,
                                "Synthetic zero-balance treasury clone"))
                .willReturn(pretend200);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                NO_CUSTOM_FEES,
                                tracker201,
                                "Synthetic zero-balance treasury clone"))
                .willReturn(pretend201);
        given(
            creator.createSuccessfulSyntheticRecord(
                NO_CUSTOM_FEES,
                tracker275,
                "Synthetic no-op account update"))
            .willReturn(pretend275);
        given(
            creator.createSuccessfulSyntheticRecord(
                NO_CUSTOM_FEES,
                tracker276,
                "Synthetic no-op account update"))
            .willReturn(pretend276);
        given(
            creator.createSuccessfulSyntheticRecord(
                NO_CUSTOM_FEES,
                tracker277,
                "Synthetic no-op account update"))
            .willReturn(pretend277);
        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccount);
        given(accountNumbers.nodeRewardAccount()).willReturn(nodeRewardAccount);
        givenSomeTreasuryClones();
        givenSomeExtantAccounts();

        subject.publishMigrationRecords(now);

        verify(sigImpactHistorian).markEntityChanged(800L);
        verify(sigImpactHistorian).markEntityChanged(801L);
        verify(tracker800)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(800L).build(), ByteString.EMPTY);
        verify(tracker801)
                .trackAutoCreation(
                        AccountID.newBuilder().setAccountNum(801L).build(), ByteString.EMPTY);
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend800));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend801));
        verify(networkCtx).markMigrationRecordsStreamed();
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend200));
        verify(recordsHistorian)
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend201));
        verify(recordsHistorian)
            .trackPrecedingChildRecord(
                eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend275));
        verify(recordsHistorian)
            .trackPrecedingChildRecord(
                eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend276));
        verify(recordsHistorian)
            .trackPrecedingChildRecord(
                eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend277));
        final var bodies = bodyCaptor.getAllValues();
        assertEquals(rewardSynthBody, bodies.get(0).build());
        assertEquals(rewardSynthBody, bodies.get(1).build());
        assertEquals(cloneSynthBody, bodies.get(2).build());
        assertEquals(cloneSynthBody, bodies.get(3).build());
        assertEquals(nodeStakingBody, bodies.get(4).build());
        assertEquals(accountStakingBody, bodies.get(5).build());
        assertEquals(notStakingBody, bodies.get(6).build());
        // and:
        verify(treasuryCloner).forgetScannedSystemAccounts();
    }

    @Test
    void ifContextIndicatesContractRenewRecordsNeedToBeStreamedThenDoesSo() {
        final ArgumentCaptor<TransactionBody.Builder> bodyCaptor =
                forClass(TransactionBody.Builder.class);
        final ArgumentCaptor<ExpirableTxnRecord.Builder> recordCaptor =
                forClass(ExpirableTxnRecord.Builder.class);

        final var contractUpdateSynthBody1 =
                factory.synthContractAutoRenew(
                                contract1Id.asNum(), contract1Expiry, contract1Id.toGrpcAccountId())
                        .build();
        final var contractUpdateSynthBody2 =
                factory.synthContractAutoRenew(
                                contract2Id.asNum(), contract2Expiry, contract2Id.toGrpcAccountId())
                        .build();

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);
        given(merkleAccount.isSmartContract()).willReturn(true);
        given(merkleAccount.getExpiry()).willReturn(contract1Expiry).willReturn(contract2Expiry);
        MigrationRecordsManager.setExpiryJustEnabled(true);

        subject.publishMigrationRecords(now);

        MigrationRecordsManager.setExpiryJustEnabled(false);

        verify(recordsHistorian, times(2))
                .trackPrecedingChildRecord(
                        eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), recordCaptor.capture());
        verify(networkCtx).markMigrationRecordsStreamed();

        final var bodies = bodyCaptor.getAllValues();
        assertEquals(contractUpdateSynthBody1, bodies.get(0).build());
        assertEquals(contractUpdateSynthBody2, bodies.get(1).build());

        final var records = recordCaptor.getAllValues();
        // since txnId will be set at late point, will set txnId for comparing
        assertEquals(
                expectedContractUpdateRecord(contract1Id, contract1Expiry).build(),
                records.get(0).setTxnId(new TxnId()).build());
        assertEquals(
                expectedContractUpdateRecord(contract2Id, contract2Expiry).build(),
                records.get(1).setTxnId(new TxnId()).build());
    }

    @Test
    void ifExpiryNotJustEnabledThenContractRenewRecordsAreNotStreamed() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(now);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(recordsHistorian);
    }

    @Test
    void doesNothingIfRecordsAlreadyStreamed() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(false);

        subject.publishMigrationRecords(now);

        given(networkCtx.areMigrationRecordsStreamed()).willReturn(true);
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);

        subject.publishMigrationRecords(now);

        given(consensusTimeTracker.unlimitedPreceding()).willReturn(false);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
        verify(networkCtx, never()).consensusTimeOfLastHandledTxn();
        verify(networkCtx, never()).markMigrationRecordsStreamed();
    }

    @Test
    void doesntStreamRewardAccountCreationIfNotGenesis() {
        given(consensusTimeTracker.unlimitedPreceding()).willReturn(true);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(Instant.MAX);

        subject.publishMigrationRecords(now);

        verifyNoInteractions(sigImpactHistorian);
        verifyNoInteractions(recordsHistorian);
    }

    private ExpirableTxnRecord.Builder expectedContractUpdateRecord(
        final EntityId num, final long newExpiry) {
        final var receipt = TxnReceipt.newBuilder().setStatus(SUCCESS_LITERAL).build();

        final var memo =
            String.format(MigrationRecordsManager.AUTO_RENEW_MEMO_TPL, num.num(), newExpiry);
        return ExpirableTxnRecord.newBuilder()
            .setTxnId(new TxnId())
            .setMemo(memo)
            .setReceipt(receipt);
    }


    private TransactionBody expectedSyntheticRewardAccount() {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(EXPECTED_KEY)
                        .setMemo(EMPTY_MEMO)
                        .setInitialBalance(0)
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(fundingExpiry - now.getEpochSecond()))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
    }

    private TransactionBody expectedSyntheticTreasuryClone() {
        final var txnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(MiscUtils.asKeyUnchecked(pretendTreasuryKey))
                        .setMemo("123")
                        .setInitialBalance(0)
                    .setReceiverSigRequired(true)
                        .setAutoRenewPeriod(
                                Duration.newBuilder()
                                        .setSeconds(pretendExpiry - now.getEpochSecond()))
                        .build();
        return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
    }

    private TransactionBody expectedExistingStakedTo(final long n, final long stakedId) {
        final var bodyBuilder =
            CryptoUpdateTransactionBody.newBuilder()
                .setAccountIDToUpdate(AccountID.newBuilder().setAccountNum(n).build())
                .setKey(MiscUtils.asKeyUnchecked(pretendTreasuryKey))
                .setMemo(StringValue.of(pretendMemo))
                .setDeclineReward(BoolValue.newBuilder().setValue(true).build())
                .setReceiverSigRequiredWrapper(BoolValue.newBuilder().setValue(true).build())
                .setMaxAutomaticTokenAssociations(Int32Value.newBuilder().setValue(pretendMaxAutoAssociations).build())
                .setAutoRenewPeriod(
                    Duration.newBuilder()
                        .setSeconds(pretendAutoRenew))
                .setExpirationTime(Timestamp.newBuilder().setSeconds(pretendExpiry).build());
        if (stakedId < 0) {
           bodyBuilder.setStakedNodeId(-stakedId - 1);
        } else if (stakedId > 0) {
            bodyBuilder.setStakedAccountId(AccountID.newBuilder().setAccountNum(stakedId).build());
        }
        return TransactionBody.newBuilder().setCryptoUpdateAccount(bodyBuilder).build();
    }

    private void givenSomeTreasuryClones() {
        treasuryClones.addAll(
                List.of(
                        accountWith(pretendExpiry, pretendTreasuryKey),
                        accountWith(pretendExpiry, pretendTreasuryKey)));
        treasuryClones.get(0).setKey(EntityNum.fromLong(200L));
        treasuryClones.get(1).setKey(EntityNum.fromLong(201L));
        given(treasuryCloner.getClonesCreated()).willReturn(treasuryClones);
    }

    private void givenSomeExtantAccounts() {
        for (long n = 275; n <= 277; n++) {
            final long stakedId;
            if (n == 275) {
                stakedId = pretendNodeStakedId;
            } else if (n == 276) {
                stakedId = pretendStakedAccountId;
            } else {
                stakedId = 0L;
            }
            final var account = new HederaAccountCustomizer()
                .isReceiverSigRequired(true)
                .isDeleted(false)
                .expiry(pretendExpiry)
                .isDeclinedReward(true)
                .memo(pretendMemo)
                .isSmartContract(false)
                .stakedId(stakedId)
                .maxAutomaticAssociations(pretendMaxAutoAssociations)
                .key(pretendTreasuryKey)
                .autoRenewPeriod(pretendAutoRenew)
                .customizing(new MerkleAccount());
            account.setKey(EntityNum.fromLong(n));
            skippedCandidateClones.add(account);
        }
        given(treasuryCloner.getSkippedCandidateClones()).willReturn(skippedCandidateClones);
    }

    private static final long pretendStakedAccountId = 666L;
    private static final long pretendNodeStakedId = -2L;
    private static final int pretendMaxAutoAssociations = 20;
    private static final long pretendExpiry = 2 * now.getEpochSecond();
    private static final long pretendAutoRenew = 1_234_567;
    private static final String pretendMemo = "Thoughts and images";
    private static final JKey pretendTreasuryKey =
            new JEd25519Key("a123456789a123456789a123456789a1".getBytes());
}
