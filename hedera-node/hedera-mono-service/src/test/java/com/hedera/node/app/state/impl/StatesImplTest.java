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
package com.hedera.node.app.state.impl;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatesImplTest {
    //    private static final String ACCOUNTS = "ACCOUNTS";
    //    private static final String ALIASES = "ALIASES";
    //    private static final String TOKENS = "TOKENS";
    //    @Mock private ServicesState state;
    //    @Mock private MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts;
    //    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;
    //    @Mock private AccountStorageAdapter accountsAdapter;
    //    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    //    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    //    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    //    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    //    @Mock private TokenRelStorageAdapter tokenAssociations;
    //    @Mock private MerkleScheduledTransactions scheduleTxs;
    //    @Mock private MerkleNetworkContext networkCtx;
    //    @Mock private AddressBook addressBook;
    //    @Mock private MerkleSpecialFiles specialFiles;
    //    @Mock private UniqueTokenMapAdapter uniqueTokens;
    //    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    //    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    //    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
    //
    //    private StatesImpl subject;
    //
    //    @BeforeEach
    //    void setUp() {
    //        subject = new StatesImpl();
    //    }
    //
    //    @Test
    //    void updatesChildrenFromImmutableState() {
    //        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
    //        givenStateWithMockChildren();
    //        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
    //        given(state.isInitialized()).willReturn(true);
    //
    //        subject.updateChildren(state);
    //        assertChildrenAreExpectedMocks();
    //        assertEquals(lastHandledTime, subject.getChildren().signedAt());
    //    }
    //
    //    @Test
    //    void returnsInMemoryAccountsWhenAppropriate() {
    //
    //        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
    //        givenStateWithMockChildren();
    //        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
    //        given(state.isInitialized()).willReturn(true);
    //        given(accountsAdapter.getInMemoryAccounts()).willReturn(inMemoryAccounts);
    //
    //        subject.updateChildren(state);
    //
    //        final var state = subject.get(ACCOUNTS);
    //
    //        assertEquals(lastHandledTime, state.getLastModifiedTime());
    //        assertTrue(state instanceof InMemoryState);
    //
    //        assertThrows(NotImplementedException.class, () -> subject.get(TOKENS));
    //    }
    //
    //    @Test
    //    void returnsAliasesFromChildren() {
    //        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
    //        givenStateWithMockChildren();
    //        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
    //        given(state.isInitialized()).willReturn(true);
    //
    //        subject.updateChildren(state);
    //
    //        final var state = subject.get(ALIASES);
    //
    //        assertEquals(lastHandledTime, state.getLastModifiedTime());
    //        assertTrue(state instanceof RebuiltState);
    //    }
    //
    //    @Test
    //    void returnsOnDiskAccountsWhenAppropriate() {
    //        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
    //        givenStateWithMockChildren();
    //        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
    //        given(state.isInitialized()).willReturn(true);
    //        given(accountsAdapter.areOnDisk()).willReturn(true);
    //        given(accountsAdapter.getOnDiskAccounts()).willReturn(onDiskAccounts);
    //
    //        subject.updateChildren(state);
    //
    //        final var state = subject.get(ACCOUNTS);
    //
    //        assertEquals(lastHandledTime, state.getLastModifiedTime());
    //        assertTrue(state instanceof OnDiskState);
    //    }
    //
    //    private void givenStateWithMockChildren() {
    //        given(state.accounts()).willReturn(accountsAdapter);
    //        given(state.storage()).willReturn(storage);
    //        given(state.contractStorage()).willReturn(contractStorage);
    //        given(state.topics()).willReturn(topics);
    //        given(state.tokens()).willReturn(tokens);
    //        given(state.tokenAssociations()).willReturn(tokenAssociations);
    //        given(state.scheduleTxs()).willReturn(scheduleTxs);
    //        given(state.networkCtx()).willReturn(networkCtx);
    //        given(state.addressBook()).willReturn(addressBook);
    //        given(state.specialFiles()).willReturn(specialFiles);
    //        given(state.uniqueTokens()).willReturn(uniqueTokens);
    //        given(state.runningHashLeaf()).willReturn(runningHashLeaf);
    //        given(state.aliases()).willReturn(aliases);
    //        given(state.stakingInfo()).willReturn(stakingInfo);
    //    }
    //
    //    private void assertChildrenAreExpectedMocks() {
    //        final var children = subject.getChildren();
    //
    //        assertSame(accountsAdapter, children.accounts());
    //        assertSame(storage, children.storage());
    //        assertSame(contractStorage, children.contractStorage());
    //        assertSame(topics, children.topics());
    //        assertSame(tokens, children.tokens());
    //        assertSame(tokenAssociations, children.tokenAssociations());
    //        assertSame(scheduleTxs, children.schedules());
    //        assertSame(networkCtx, children.networkCtx());
    //        assertSame(addressBook, children.addressBook());
    //        assertSame(specialFiles, children.specialFiles());
    //        assertSame(uniqueTokens, children.uniqueTokens());
    //        assertSame(runningHashLeaf, children.runningHashLeaf());
    //        assertSame(aliases, children.aliases());
    //        assertSame(stakingInfo, children.stakingInfo());
    //    }
}
