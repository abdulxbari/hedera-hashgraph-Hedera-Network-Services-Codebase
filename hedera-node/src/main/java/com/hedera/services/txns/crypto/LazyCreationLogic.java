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
package com.hedera.services.txns.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignaturePair;
import org.apache.commons.codec.DecoderException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;

@Singleton
public class LazyCreationLogic {
    private final AliasManager aliasManager;
    private final AccountStore accountStore;

    @Inject
    public LazyCreationLogic(
            final AliasManager aliasManager, final AccountStore accountStore) {
        this.aliasManager = aliasManager;
        this.accountStore = accountStore;
    }

    public JKey recoverPubKeyFromSigMap(byte[] message, List<SignaturePair> sigs) {
        var lazyCreateKey = sigs.stream()
                .filter(sp -> sp.getECDSASecp256K1().size() != 0)
                .map(sp -> sp.getECDSASecp256K1().toByteArray())
                .flatMap(s -> Arrays.stream(recoverPubKeyFromSignature(s, message)))
                .filter(pk -> !Arrays.equals(pk, new byte[64]))
                .filter(pk -> lazyCreatedAccountExists(pk))
                .findFirst();

        if (lazyCreateKey.isPresent()) {
            var lk = lazyCreateKey.get();
            try {
                final Key key = Key.parseFrom(lk);
                final JKey jKey = JKey.mapKey(key);
                return jKey;
            } catch (InvalidProtocolBufferException
                     | DecoderException
                     | IllegalArgumentException ignore) {
                return null;
            }
        }

        return null;
    }

    public void tryToComplete(ByteString evmAddress, JKey payerKey) {
        final var aliasedAccountId = aliasManager.lookupIdBy(evmAddress).toId();
        final var account = accountStore.loadAccount(aliasedAccountId);
        final var isLazyCreated = account.getAlias().equals(evmAddress) && account.getKey() == null;
        if (isLazyCreated) {
            account.setKey(payerKey);
            accountStore.commitAccount(account);
        }
    }

    private byte[][]  recoverPubKeyFromSignature(byte[] signature, byte[] message) {
        final byte[] r = new byte[32];
        System.arraycopy(signature, 0, r, 0, 32);
        final byte[] s = new byte[32];
        System.arraycopy(signature, 32, s, 0, 32);

        byte[] pubKey0Bytes = new byte[64];
        byte[] pubKey1Bytes = new byte[64];
        byte[] pubKey2Bytes = new byte[64];
        byte[] pubKey3Bytes = new byte[64];

        try {
            pubKey0Bytes = EthTxSigs.recoverCompressedPubKey(EthTxSigs.extractSig(0, r, s, message));
        } catch (IllegalArgumentException ignore) {
        }

        try {
            pubKey1Bytes = EthTxSigs.recoverCompressedPubKey(EthTxSigs.extractSig(1, r, s, message));
        } catch (IllegalArgumentException ignore) {
        }

        try {
            pubKey2Bytes = EthTxSigs.recoverCompressedPubKey(EthTxSigs.extractSig(2, r, s, message));
        } catch (IllegalArgumentException ignore) {
        }

        try {
            pubKey3Bytes = EthTxSigs.recoverCompressedPubKey(EthTxSigs.extractSig(3, r, s, message));
        } catch (IllegalArgumentException ignore) {
        }

        return new byte[][] {pubKey0Bytes, pubKey1Bytes, pubKey2Bytes, pubKey3Bytes};
    }

    private boolean lazyCreatedAccountExists(byte[] ecdsaPubKey) {
        final var evmAddress = ByteString.copyFrom(EthTxSigs.recoverAddressFromPubKey(ecdsaPubKey));
//        final var accountId = aliasManager.lookupIdBy(evmAddress).toGrpcAccountId();
//        if (accountId.equals(AccountID.getDefaultInstance())) {
//            return false;
//        }
//
//        return ledger.alias(accountId).equals(evmAddress) && ledger.key(accountId) == null;

        final var aliasedAccountId = aliasManager.lookupIdBy(evmAddress).toId();
        if (aliasedAccountId.equals(Id.DEFAULT)) {
            return false;
        }

        final var account = accountStore.loadAccount(aliasedAccountId);
        return account.getAlias().equals(evmAddress) && account.getKey() == null;
    }
}
