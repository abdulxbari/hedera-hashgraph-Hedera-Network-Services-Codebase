/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.disk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.hedera.node.app.state.merkle.MerkleTestBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A variety of robust tests for the on-disk merkle data structure, especially including
 * serialization to/from disk (under normal operation) and to/from saved state. These tests use a
 * more complex map, with full objects to store and retrieve objects from the virtual map, and when
 * serializing for hashing, and for serializing when saving state.
 */
class OnDiskTest extends MerkleTestBase {
    private static final String SERVICE_NAME = "CryptoService";
    private static final String ACCOUNT_STATE_KEY = "Account";

    private Path storageDir;
    private Schema schema;
    private StateDefinition<AccountID, Account> def;
    private StateMetadata<AccountID, Account> md;
    private VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> virtualMap;
    private Configuration config;

    @BeforeEach
    void setUp() throws IOException {
        setupConstructableRegistry();
        storageDir = TemporaryFileBuilder.buildTemporaryDirectory();

        def = StateDefinition.onDisk(ACCOUNT_STATE_KEY, new AccountIDCodec(), new AccountCodec(), 100);

        //noinspection rawtypes
        schema = new Schema(version(1, 0, 0)) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(def);
            }
        };

        md = new StateMetadata<>(SERVICE_NAME, schema, def);

        final var builder = new JasperDbBuilder<OnDiskKey<AccountID>, OnDiskValue<Account>>()
                // Force all hashes to disk, to make sure we're going through all the
                // serialization paths we can
                .hashesRamToDiskThreshold(0)
                .storageDir(storageDir)
                .maxNumOfKeys(100)
                .preferDiskBasedIndexes(true)
                .keySerializer(new OnDiskKeySerializer<>(md))
                .virtualLeafRecordSerializer(new VirtualLeafRecordSerializer<>(
                        (short) 1,
                        DataFileCommon.VARIABLE_DATA_SIZE,
                        new OnDiskKeySerializer<>(md),
                        (short) 1,
                        DataFileCommon.VARIABLE_DATA_SIZE,
                        new OnDiskValueSerializer<>(md),
                        false));

        virtualMap = new VirtualMap<>(StateUtils.computeLabel(SERVICE_NAME, ACCOUNT_STATE_KEY), builder);

        this.config = Mockito.mock(Configuration.class);
        final var hederaConfig = Mockito.mock(HederaConfig.class);
        lenient().when(config.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
    }

    <K extends Comparable<K>, V> VirtualMap<OnDiskKey<K>, OnDiskValue<V>> copyHashAndFlush(
            VirtualMap<OnDiskKey<K>, OnDiskValue<V>> map) {
        // Make the fast copy
        final var copy = map.copy();

        // Hash the now immutable map
        CRYPTO.digestTreeSync(map);

        // Flush to disk
        final VirtualRootNode<?, ?> root = map.getChild(1);
        root.enableFlush();
        map.release();
        try {
            root.waitUntilFlushed();
        } catch (InterruptedException e) {
            System.err.println("Unable to complete the test, the root node never flushed!");
            throw new RuntimeException(e);
        }

        // And we're done
        return copy;
    }

    @Test
    void populateTheMapAndFlushToDiskAndReadBack() throws IOException {
        // Populate the data set and flush it all to disk
        final var ws = new OnDiskWritableKVState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = new Account(id, "Account " + i, i);
            ws.put(id, acct);
        }
        ws.commit();
        virtualMap = copyHashAndFlush(virtualMap);

        // We will now make another fast copy of our working copy of the tree.
        // Then we will hash the immutable copy and write it out. Then we will
        // release the immutable copy.
        virtualMap.copy(); // throw away the copy, we won't use it
        CRYPTO.digestTreeSync(virtualMap);

        final var snapshotDir = TemporaryFileBuilder.buildTemporaryDirectory("snapshot");
        final byte[] serializedBytes = writeTree(virtualMap, snapshotDir);

        // Before we can read the data back, we need to register the data types
        // I plan to deserialize.
        final var r = new MerkleSchemaRegistry(registry, SERVICE_NAME);
        r.register(schema);

        // read it back now as our map and validate the data come back fine
        virtualMap = parseTree(serializedBytes, snapshotDir);
        final var rs = new OnDiskReadableKVState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = rs.get(id);
            assertThat(acct).isNotNull();
            assertThat(acct.accountID()).isEqualTo(id);
            assertThat(acct.memo()).isEqualTo("Account " + i);
            assertThat(acct.balance).isEqualTo(i);
        }
    }

    @Test
    void populateFlushToDisk() {
        final var ws = new OnDiskWritableKVState<>(md, virtualMap);
        for (int i = 1; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = new Account(id, "Account " + i, i);
            ws.put(id, acct);
        }
        ws.commit();
        virtualMap = copyHashAndFlush(virtualMap);

        final var rs = new OnDiskReadableKVState<>(md, virtualMap);
        for (int i = 1; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = rs.get(id);
            assertThat(acct).isNotNull();
            assertThat(acct.accountID()).isEqualTo(id);
            assertThat(acct.memo()).isEqualTo("Account " + i);
            assertThat(acct.balance).isEqualTo(i);
        }
    }

    /*****************************************************************************
     * The classes and method below this point are helpers for this test. They
     * include fake objects and serialization methods, emulating what a service
     * would do.
     ****************************************************************************/
    private record AccountID(long shard, long realm, long num) implements Comparable<AccountID> {
        @Override
        public int compareTo(AccountID other) {
            if (shard != other.shard) {
                return Long.compare(shard, other.shard);
            }

            if (realm != other.realm) {
                return Long.compare(realm, other.realm);
            }

            if (num != other.num) {
                return Long.compare(num, other.num);
            }

            return 0;
        }
    }

    private record Account(@NonNull OnDiskTest.AccountID accountID, @NonNull String memo, long balance) {}

    private static final class AccountIDCodec implements Codec<AccountID> {

        @NonNull
        @Override
        public AccountID parse(@NonNull ReadableSequentialData input) {
            final long shard = input.readLong();
            final long realm = input.readLong();
            final long num = input.readLong();
            return new AccountID(shard, realm, num);
        }

        @NonNull
        @Override
        public AccountID parseStrict(@NonNull ReadableSequentialData input) {
            return parse(input);
        }

        @Override
        public void write(@NonNull AccountID value, @NonNull WritableSequentialData output) throws IOException {
            output.writeLong(value.shard);
            output.writeLong(value.realm);
            output.writeLong(value.num);
        }

        @Override
        public int measure(@NonNull ReadableSequentialData readableSequentialData) {
            // This implementation doesn't need to read from input.
            return Long.BYTES * 3;
        }

        @Override
        public int measureRecord(AccountID accountID) {
            return 150;
        }

        @Override
        public boolean fastEquals(@NonNull AccountID item, @NonNull ReadableSequentialData input) {
            try {
                return item.equals(parse(input));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private static final class AccountCodec implements Codec<Account> {
        private final AccountIDCodec accountIDSerdes = new AccountIDCodec();

        @NonNull
        @Override
        public Account parse(@NonNull ReadableSequentialData input) throws IOException {
            final var id = accountIDSerdes.parse(input);
            final int memoLen = input.readInt();
            final byte[] memoBytes = new byte[memoLen];
            input.readBytes(memoBytes);
            final var memo = new String(memoBytes, StandardCharsets.UTF_8);
            final var balance = input.readLong();
            return new Account(id, memo, balance);
        }

        @NonNull
        @Override
        public Account parseStrict(@NonNull ReadableSequentialData input) throws IOException {
            return parse(input);
        }

        @Override
        public void write(@NonNull Account acct, @NonNull WritableSequentialData output) throws IOException {
            // id
            accountIDSerdes.write(acct.accountID(), output);
            // memo
            final var bb = StandardCharsets.UTF_8.encode(acct.memo());
            output.writeInt(bb.limit());
            output.writeBytes(bb.array(), 0, bb.limit());
            // balance
            output.writeLong(acct.balance);
        }

        @Override
        public int measure(@NonNull ReadableSequentialData readableSequentialData) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public int measureRecord(Account account) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public boolean fastEquals(@NonNull Account account, @NonNull ReadableSequentialData readableSequentialData) {
            throw new UnsupportedOperationException("Not used");
        }
    }

    @Test
    void toStringWorks() {
        final var key = new OnDiskKey<>(md);
        final var string = key.toString();
        assertThat(string).isEqualTo("OnDiskKey{key=null}");
    }
}
