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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UniqueTokenAdapterTest {

    public UniqueTokenAdapter merkleSubject;
    public UniqueTokenAdapter virtualSubject;

    @BeforeEach
    void setUp() {
        merkleSubject =
                new UniqueTokenAdapter(
                        new MerkleUniqueToken(
                                EntityId.fromNum(123L),
                                "hello".getBytes(),
                                RichInstant.MISSING_INSTANT));
        merkleSubject.setSpender(EntityId.fromNum(456L));

        virtualSubject =
                new UniqueTokenAdapter(
                        new UniqueTokenValue(
                                123L, 456L, "hello".getBytes(), RichInstant.MISSING_INSTANT));
    }

    @Test
    void testIsVirtual() {
        assertTrue(virtualSubject.isVirtual());
        assertFalse(merkleSubject.isVirtual());
    }

    @Test
    void testIsImmutable() {
        assertFalse(virtualSubject.isImmutable());
        virtualSubject.uniqueTokenValue().copy();
        assertTrue(virtualSubject.isImmutable());

        assertFalse(merkleSubject.isImmutable());
        merkleSubject.merkleUniqueToken().copy();
        assertTrue(merkleSubject.isImmutable());
    }

    @Test
    void testConstructedValues() {
        assertEquals(123L, virtualSubject.getOwner().num());
        assertEquals(456L, virtualSubject.getSpender().num());
        assertArrayEquals("hello".getBytes(), virtualSubject.getMetadata());
        assertEquals(0L, virtualSubject.getPackedCreationTime());

        assertEquals(123L, merkleSubject.getOwner().num());
        assertEquals(456L, merkleSubject.getSpender().num());
        assertArrayEquals("hello".getBytes(), merkleSubject.getMetadata());
        assertEquals(0L, merkleSubject.getPackedCreationTime());
    }

    @Test
    void testSettersUpdateValues() {
        virtualSubject.setOwner(EntityId.fromNum(987L));
        assertEquals(987L, virtualSubject.getOwner().num());
        virtualSubject.setSpender(EntityId.fromNum(654L));
        assertEquals(654L, virtualSubject.getSpender().num());
        virtualSubject.setMetadata("goodbye".getBytes());
        assertArrayEquals("goodbye".getBytes(), virtualSubject.getMetadata());
        virtualSubject.setPackedCreationTime(999L);
        assertEquals(999L, virtualSubject.getPackedCreationTime());

        merkleSubject.setOwner(EntityId.fromNum(987L));
        assertEquals(987L, merkleSubject.getOwner().num());
        merkleSubject.setSpender(EntityId.fromNum(654L));
        assertEquals(654L, merkleSubject.getSpender().num());
        merkleSubject.setMetadata("goodbye".getBytes());
        assertArrayEquals("goodbye".getBytes(), merkleSubject.getMetadata());
        merkleSubject.setPackedCreationTime(999L);
        assertEquals(999L, merkleSubject.getPackedCreationTime());
    }

    @Test
    void testEqualsForSelfReferentialCases() {
        assertTrue(virtualSubject.equals(virtualSubject));
        assertTrue(merkleSubject.equals(merkleSubject));
    }

    @Test
    void testEqualsForSameTypes() {
        assertTrue(
                virtualSubject.equals(
                        UniqueTokenAdapter.wrap(
                                new UniqueTokenValue(
                                        123L,
                                        456L,
                                        "hello".getBytes(),
                                        RichInstant.MISSING_INSTANT))));

        MerkleUniqueToken merkleUniqueToken =
                new MerkleUniqueToken(
                        EntityId.fromNum(123L), "hello".getBytes(), RichInstant.MISSING_INSTANT);
        merkleUniqueToken.setSpender(EntityId.fromNum(456L));
        assertTrue(merkleSubject.equals(UniqueTokenAdapter.wrap(merkleUniqueToken)));
    }

    @Test
    void testEqualsFalseForVirtualAndMerkleComparisons() {
        assertFalse(virtualSubject.equals(merkleSubject));
        assertFalse(merkleSubject.equals(virtualSubject));
    }

    @Test
    void testEqualsFalseForNullComparisons() {
        assertFalse(virtualSubject.equals(null));
        assertFalse(merkleSubject.equals(null));
    }

    @Test
    void testEqualsFalseForDifferentTypeComparisons() {
        assertFalse(virtualSubject.equals(new Object()));
        assertFalse(merkleSubject.equals(new Object()));
    }

    @Test
    void testEqualsFalseWhenFieldsDiffer() {
        assertFalse(
                virtualSubject.equals(
                        UniqueTokenAdapter.wrap(
                                new UniqueTokenValue(
                                        1L,
                                        456L,
                                        "hello".getBytes(),
                                        RichInstant.MISSING_INSTANT))));
        assertFalse(
                virtualSubject.equals(
                        UniqueTokenAdapter.wrap(
                                new UniqueTokenValue(
                                        123L,
                                        4L,
                                        "hello".getBytes(),
                                        RichInstant.MISSING_INSTANT))));
        assertFalse(
                virtualSubject.equals(
                        UniqueTokenAdapter.wrap(
                                new UniqueTokenValue(
                                        123L, 456L, "h".getBytes(), RichInstant.MISSING_INSTANT))));
        assertFalse(
                virtualSubject.equals(
                        UniqueTokenAdapter.wrap(
                                new UniqueTokenValue(
                                        123L, 456L, "hello".getBytes(), new RichInstant(3, 4)))));

        UniqueTokenAdapter merkleValue =
                UniqueTokenAdapter.wrap(
                        new MerkleUniqueToken(
                                EntityId.fromNum(123L),
                                "hello".getBytes(),
                                RichInstant.MISSING_INSTANT));

        merkleValue.setSpender(EntityId.fromNum(456L));

        merkleValue.setOwner(EntityId.fromNum(1L));
        assertFalse(merkleSubject.equals(merkleValue));

        merkleValue.setOwner(EntityId.fromNum(123L));
        merkleValue.setSpender(EntityId.fromNum(4L));
        assertFalse(merkleSubject.equals(merkleValue));

        merkleValue.setSpender(EntityId.fromNum(456L));
        merkleValue.setMetadata("h".getBytes());
        assertFalse(merkleSubject.equals(merkleValue));

        merkleValue.setMetadata("hello".getBytes());
        merkleValue.setPackedCreationTime(22L);
        assertFalse(merkleSubject.equals(merkleValue));

        merkleValue.setPackedCreationTime(0L);
        assertTrue(merkleSubject.equals(merkleValue));

        assertFalse(merkleSubject.equals(UniqueTokenAdapter.newEmptyMerkleToken()));
    }

    @Test
    void testDirectAccessGetters() {
        Assertions.assertNotNull(merkleSubject.merkleUniqueToken());
        Assertions.assertNull(merkleSubject.uniqueTokenValue());

        Assertions.assertNull(virtualSubject.merkleUniqueToken());
        Assertions.assertNotNull(virtualSubject.uniqueTokenValue());
    }
}
