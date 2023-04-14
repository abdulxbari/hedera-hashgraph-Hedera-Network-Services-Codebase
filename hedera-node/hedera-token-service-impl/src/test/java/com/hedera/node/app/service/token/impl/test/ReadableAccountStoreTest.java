/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.ledger.accounts.AliasManager.keyAliasToEVMAddress;
import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.test.handlers.CryptoHandlerTestBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

// FUTURE: Once we have protobuf generated object need to replace all JKeys.
@ExtendWith(MockitoExtension.class)
class ReadableAccountStoreTest extends CryptoHandlerTestBase {
    private ReadableAccountStore subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = readableStore;
    }

    @Test
    void getsKeyIfAlias() {
        given(aliases.get(payerAlias.alias().asUtf8String()))
                .willReturn(new EntityNumValue(accountNum));
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);

        final var result = subject.getKey(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(accountHederaKey, result.key());
    }

    @Test
    void getsKeyIfEvmAddress() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);

        final var result = subject.getKey(contractAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(contractHederaKey, result.key());
    }

    @Test
    void getsNullKeyIfMissingEvmAddress() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfMissingContract() {
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);

        var result = subject.getKey(contract);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contract);

        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfNotSmartContract() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);

        var result = subject.getKey(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void failsIfContractDeleted() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.isDeleted()).willReturn(true);

        var result = subject.getKey(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);
        assertTrue(result.failed());
        assertEquals(INVALID_CONTRACT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void getsKeyIfAccount() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);

        final var result = subject.getKey(id);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(accountHederaKey, result.key());
    }

    @Test
    void getsNullKeyIfMissingAlias() {
        given(aliases.get(payerAlias.alias().asUtf8String())).willReturn(new EntityNumValue());

        final var result = subject.getKey(payerAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfMissingAccount() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);

        final var result = subject.getKey(id);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsMirrorAddress() {
        final var num = EntityNum.fromLong(accountNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount =
                AccountID.newBuilder().alias(Bytes.wrap(mirrorAddress.toArrayUnsafe())).build();

        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);

        final var result = subject.getKey(mirrorAccount);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(accountHederaKey, result.key());
    }

    @Test
    void failsIfMirrorAddressDoesntExist() {
        final var num = EntityNum.fromLong(accountNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount = AccountID.newBuilder()
                .alias(Bytes.wrap(mirrorAddress.toArrayUnsafe()))
                .build();

        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);

        final var result = subject.getKey(mirrorAccount);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsMirrorAddressNumForContract() {
        final var num = EntityNum.fromLong(contract.contractNum());
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount = ContractID.newBuilder()
                .evmAddress(Bytes.wrap(mirrorAddress.toArrayUnsafe()))
                .build();

        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);

        final var result = subject.getKey(mirrorAccount);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(contractHederaKey, result.key());
    }

    @Test
    void derivesEVMAddressIfNotMirror() {
        final var aliasBytes = Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
        final var ecdsaAlias = Bytes.wrap(aliasBytes);
        final var mirrorAccount = ContractID.newBuilder().evmAddress(ecdsaAlias).build();
        final var evmAddress = keyAliasToEVMAddress(ecdsaAlias);
        final var evmAddressString = Bytes.wrap(evmAddress);

        given(aliases.get(ecdsaAlias.asUtf8String())).willReturn(null);
        given(aliases.get(evmAddressString.asUtf8String())).willReturn(new EntityNumValue(contract.contractNum()));

        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);

        final var result = subject.getKey(mirrorAccount);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(contractHederaKey, result.key());
    }

    @Test
    void getsKeyIfPayerAliasAndReceiverSigRequired() {
        given(aliases.get(payerAlias.alias().asUtf8String()))
                .willReturn(new EntityNumValue(accountNum));
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.isReceiverSigRequired()).willReturn(true);

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(accountHederaKey, result.key());
    }

    @Test
    void getsKeyIfPayerAccountAndReceiverSigRequired() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.isReceiverSigRequired()).willReturn(true);

        final var result = subject.getKeyIfReceiverSigRequired(id);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(accountHederaKey, result.key());
    }

    @Test
    void getsNullKeyFromReceiverSigRequiredIfMissingAlias() {
        given(aliases.get(payerAlias.alias().asUtf8String())).willReturn(new EntityNumValue());

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromReceiverSigRequiredIfMissingAccount() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);

        final var result = subject.getKeyIfReceiverSigRequired(id);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyIfAndReceiverSigNotRequired() {
        given(aliases.get(payerAlias.alias().asUtf8String()))
                .willReturn(new EntityNumValue(accountNum));
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromAccountIfReceiverKeyNotRequired() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(id);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void getsNullKeyFromContractIfReceiverKeyNotRequired() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) contractHederaKey);
        given(account.isSmartContract()).willReturn(true);
        given(account.isReceiverSigRequired()).willReturn(false);

        final var result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsJContractIDKey() {
        final var mockKey = mock(JContractIDKey.class);

        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn(mockKey);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsEmpty() {
        final var key = new JEd25519Key(new byte[0]);
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn(key);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsIfKeyIsNull() {
        given(aliases.get(contractAlias.evmAddress().asUtf8String()))
                .willReturn(new EntityNumValue(contract.contractNum()));
        given(accounts.get(EntityNumVirtualKey.fromLong(contract.contractNum())))
                .willReturn(account);
        given(account.getAccountKey()).willReturn(null);
        given(account.isSmartContract()).willReturn(true);

        var result = subject.getKey(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(contractAlias);

        assertTrue(result.failed());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsKeyValidationWhenKeyReturnedIsNull() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn(null);

        var result = subject.getKey(id);
        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(id);
        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());
    }

    @Test
    void failsKeyValidationWhenKeyReturnedIsEmpty() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn(new JKeyList());

        var result = subject.getKey(id);

        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());

        result = subject.getKeyIfReceiverSigRequired(id);

        assertTrue(result.failed());
        assertEquals(ACCOUNT_IS_IMMUTABLE, result.failureReason());
        assertNull(result.key());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAccount() {
        // given
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getMemo()).willReturn("");
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.getExpiry()).willReturn(5L);
        given(account.getBalance()).willReturn(7L * HBARS_TO_TINYBARS);
        given(account.getMemo()).willReturn("Hello World");
        given(account.isDeleted()).willReturn(true);
        given(account.isReceiverSigRequired()).willReturn(true);
        given(account.getNftsOwned()).willReturn(11L);
        given(account.getMaxAutomaticAssociations()).willReturn(13);
        given(account.getUsedAutoAssociations()).willReturn(17);
        given(account.getNumAssociations()).willReturn(19);
        given(account.getNumPositiveBalances()).willReturn(23);
        given(account.getEthereumNonce()).willReturn(29L);
        given(account.getStakedToMe()).willReturn(31L);
        given(account.getStakePeriodStart()).willReturn(37L);
        given(account.totalStake()).willReturn(41L);
        given(account.isDeclinedReward()).willReturn(true);
        given(account.getAutoRenewAccount()).willReturn(new EntityId(43L, 47L, 53L));
        given(account.getAutoRenewSecs()).willReturn(59L);
        given(account.getAlias()).willReturn(ByteString.copyFrom(new byte[] {1, 2, 3}));
        given(account.isSmartContract()).willReturn(true);

        // when
        final var result = subject.getAccountById(id);

        // then
        assertThat(result).isNotEmpty();
        final var mappedAccount = result.get();
        assertEquals(accountHederaKey, mappedAccount.key());
        assertThat(mappedAccount.expiry()).isEqualTo(5L);
        assertThat(mappedAccount.tinybarBalance()).isEqualTo(7L);
        assertThat(mappedAccount.tinybarBalance()).isEqualTo(7L * HBARS_TO_TINYBARS);
        assertThat(mappedAccount.memo()).isEqualTo("Hello World");
        assertThat(mappedAccount.deleted()).isTrue();
        assertThat(mappedAccount.receiverSigRequired()).isTrue();
        assertThat(mappedAccount.numberOwnedNfts()).isEqualTo(11L);
        assertThat(mappedAccount.maxAutoAssociations()).isEqualTo(13);
        assertThat(mappedAccount.usedAutoAssociations()).isEqualTo(17);
        assertThat(mappedAccount.numberAssociations()).isEqualTo(19);
        assertThat(mappedAccount.numberPositiveBalances()).isEqualTo(23);
        assertThat(mappedAccount.ethereumNonce()).isEqualTo(29L);
        assertThat(mappedAccount.stakedToMe()).isEqualTo(31L);
        assertThat(mappedAccount.stakePeriodStart()).isEqualTo(37L);
        assertThat(mappedAccount.stakedNumber()).isEqualTo(41L);
        assertThat(mappedAccount.declineReward()).isTrue();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isEqualTo(37L);
        assertThat(mappedAccount.autoRenewAccountNumber()).isEqualTo(53L);
        assertThat(mappedAccount.autoRenewSecs()).isEqualTo(59L);
        assertThat(mappedAccount.accountNumber()).isEqualTo(id.accountNum());
        assertEquals(Bytes.wrap(new byte[] {1, 2, 3}), mappedAccount.alias());
        assertThat(mappedAccount.smartContract()).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyAccount() {
        // given
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(account);
        given(account.getAccountKey()).willReturn((JKey) accountHederaKey);
        given(account.getMemo()).willReturn("");

        // when
        final var result = subject.getAccountById(id);

        // then
        assertThat(result).isNotEmpty();
        final var mappedAccount = result.get();
        assertEquals(accountHederaKey, mappedAccount.key());
        assertThat(mappedAccount.expiry()).isZero();
        assertThat(mappedAccount.tinybarBalance()).isZero();
        assertThat(mappedAccount.tinybarBalance()).isZero();
        assertThat(mappedAccount.memo()).isEmpty();
        assertThat(mappedAccount.deleted()).isFalse();
        assertThat(mappedAccount.receiverSigRequired()).isFalse();
        assertThat(mappedAccount.numberOwnedNfts()).isZero();
        assertThat(mappedAccount.maxAutoAssociations()).isZero();
        assertThat(mappedAccount.usedAutoAssociations()).isZero();
        assertThat(mappedAccount.numberAssociations()).isZero();
        assertThat(mappedAccount.numberPositiveBalances()).isZero();
        assertThat(mappedAccount.ethereumNonce()).isZero();
        assertThat(mappedAccount.stakedToMe()).isZero();
        assertThat(mappedAccount.stakePeriodStart()).isZero();
        assertThat(mappedAccount.stakedNumber()).isZero();
        assertThat(mappedAccount.declineReward()).isFalse();
        assertThat(mappedAccount.stakeAtStartOfLastRewardedPeriod()).isZero();
        assertThat(mappedAccount.autoRenewAccountNumber()).isZero();
        assertThat(mappedAccount.autoRenewSecs()).isZero();
        assertThat(mappedAccount.accountNumber()).isEqualTo(id.accountNum());
        assertEquals(Bytes.EMPTY, mappedAccount.alias());
        assertThat(mappedAccount.smartContract()).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getsEmptyOptionalIfMissingAccount() {
        given(accounts.get(EntityNumVirtualKey.fromLong(accountNum))).willReturn(null);

        final Optional<Account> result = subject.getAccountById(id);

        assertThat(result).isEmpty();
    }
}
