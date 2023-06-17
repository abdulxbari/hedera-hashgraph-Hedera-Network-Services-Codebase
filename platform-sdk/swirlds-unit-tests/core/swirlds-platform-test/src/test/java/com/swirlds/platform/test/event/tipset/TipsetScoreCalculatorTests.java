/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.platform.Utilities.isSuperMajority;
import static com.swirlds.platform.event.tipset.Tipset.merge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.tipset.Tipset;
import com.swirlds.platform.event.tipset.TipsetBuilder;
import com.swirlds.platform.event.tipset.TipsetScoreCalculator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TipsetScoreCalculator Tests")
class TipsetScoreCalculatorTests {

    // TODO test some examples by hand instead of randomly
    // TODO non-contiguous node ID tests

    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 5;
        final NodeId windowId = new NodeId(random.nextLong(nodeCount));

        final Map<NodeId, EventDescriptor> latestEvents = new HashMap<>();

        final Map<NodeId, Long> weightMap = new HashMap<>();
        long totalWeight = 0;
        for (int i = 0; i < nodeCount; i++) {
            final long weight = random.nextLong(1_000_000);
            totalWeight += weight;
            weightMap.put(new NodeId(i), weight);
        }

        final TipsetBuilder builder = new TipsetBuilder(nodeCount, x -> (int) x, x -> weightMap.get((long) x));
        final TipsetScoreCalculator window = new TipsetScoreCalculator(
                windowId, builder, nodeCount, x -> (int) x, x -> weightMap.get(new NodeId(x)), totalWeight);

        List<EventDescriptor> previousParents = List.of();
        long runningAdvancementScore = 0;
        Tipset previousSnapshot = window.getSnapshot();

        for (int eventIndex = 0; eventIndex < 1000; eventIndex++) {
            final NodeId creator = new NodeId(random.nextLong(nodeCount));
            final long generation;
            if (latestEvents.containsKey(creator)) {
                generation = latestEvents.get(creator).getGeneration() + 1;
            } else {
                generation = 1;
            }

            final EventDescriptor selfParent = latestEvents.get(creator);
            final EventDescriptor fingerprint = new EventDescriptor(randomHash(random), creator, generation);
            latestEvents.put(creator, fingerprint);

            // Select some nodes we'd like to be our parents.
            final Set<NodeId> desiredParents = new HashSet<>();
            final int maxParentCount = random.nextInt(nodeCount);
            for (int parentIndex = 0; parentIndex < maxParentCount; parentIndex++) {
                final NodeId parent = new NodeId(random.nextInt(nodeCount));

                // We are only trying to generate a random number of parents, the exact count is unimportant.
                // So it doesn't matter if the actual number of parents is less than the number we requested.
                if (parent.equals(creator)) {
                    continue;
                }
                desiredParents.add(parent);
            }

            // Select the actual parents.
            final List<EventDescriptor> parentFingerprints = new ArrayList<>(desiredParents.size());
            if (selfParent != null) {
                parentFingerprints.add(selfParent);
            }
            for (final NodeId parent : desiredParents) {
                final EventDescriptor parentFingerprint = latestEvents.get(parent);
                if (parentFingerprint != null) {
                    parentFingerprints.add(parentFingerprint);
                }
            }

            builder.addEvent(fingerprint, parentFingerprints);

            if (creator != windowId) {
                // The following validation only needs to happen for events created by the window node.

                // Only do previous parent validation if we create two or more events in a row.
                previousParents = List.of();

                continue;
            }

            // Manually calculate the advancement score.
            final List<Tipset> parentTipsets = new ArrayList<>(parentFingerprints.size());
            for (final EventDescriptor parentFingerprint : parentFingerprints) {
                parentTipsets.add(builder.getTipset(parentFingerprint));
            }

            final Tipset newTipset;
            if (parentTipsets.isEmpty()) {
                newTipset =
                        new Tipset(nodeCount, x -> (int) x, x -> weightMap.get((long) x)).advance(creator, generation);
            } else {
                newTipset = merge(parentTipsets).advance(creator, generation);
            }

            final long expectedAdvancementScoreChange =
                    previousSnapshot.getWeightedAdvancementCount(windowId, newTipset) - runningAdvancementScore;

            // For events created by "this" node, check that the window is updated correctly.
            final long advancementScoreChange = window.addEventAndGetAdvancementScore(fingerprint);

            assertEquals(expectedAdvancementScoreChange, advancementScoreChange);

            // Special case: if we create more than one event in a row and our current parents are a
            // subset of the previous parents, then we should expect an advancement score of zero.
            boolean subsetOfPreviousParents = true;
            for (final EventDescriptor parentFingerprint : parentFingerprints) {
                if (!previousParents.contains(parentFingerprint)) {
                    subsetOfPreviousParents = false;
                    break;
                }
            }
            if (subsetOfPreviousParents) {
                assertEquals(0, advancementScoreChange);
            }
            previousParents = parentFingerprints;

            // Validate that the snapshot advances correctly.
            runningAdvancementScore += advancementScoreChange;
            if (isSuperMajority(runningAdvancementScore + weightMap.get(windowId), totalWeight)) {
                // The snapshot should have been updated.
                assertNotSame(previousSnapshot, window.getSnapshot());
                previousSnapshot = window.getSnapshot();
                runningAdvancementScore = 0;
            } else {
                // The snapshot should have not been updated.
                assertSame(previousSnapshot, window.getSnapshot());
            }
        }
    }

    // TODO this test is either broken or flaky
    @Test
    @DisplayName("Bully Test")
    void bullyTest() {
        final Random random = getRandomPrintSeed();
        final int nodeCount = 4;

        // In this test, we simulate from the perspective of node A.
        // All nodes have 1 stake, and index == id (for simplicity).
        final NodeId nodeA = new NodeId(0);
        final NodeId nodeB = new NodeId(1);
        final NodeId nodeC = new NodeId(2);
        final NodeId nodeD = new NodeId(3);

        final TipsetBuilder builder = new TipsetBuilder(nodeCount, x -> (int) x, x -> 1);
        final TipsetScoreCalculator window =
                new TipsetScoreCalculator(nodeA, builder, nodeCount, x -> (int) x, x -> 1, 4);

        final Tipset snapshot1 = window.getSnapshot();

        // Each node creates an event.
        final EventDescriptor eventA1 = new EventDescriptor(randomHash(random), nodeA, 1);
        builder.addEvent(eventA1, List.of());
        final EventDescriptor eventB1 = new EventDescriptor(randomHash(random), nodeB, 1);
        builder.addEvent(eventB1, List.of());
        final EventDescriptor eventC1 = new EventDescriptor(randomHash(random), nodeC, 1);
        builder.addEvent(eventC1, List.of());
        final EventDescriptor eventD1 = new EventDescriptor(randomHash(random), nodeD, 1);
        builder.addEvent(eventD1, List.of());

        assertEquals(0, window.getTheoreticalAdvancementScore(List.of()));
        assertEquals(0, window.addEventAndGetAdvancementScore(eventA1));
        assertSame(snapshot1, window.getSnapshot());

        // Each node creates another event. All nodes use all available other parents except the event from D.
        final EventDescriptor eventA2 = new EventDescriptor(randomHash(random), nodeA, 2);
        builder.addEvent(eventA2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptor eventB2 = new EventDescriptor(randomHash(random), nodeB, 2);
        builder.addEvent(eventB2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptor eventC2 = new EventDescriptor(randomHash(random), nodeC, 2);
        builder.addEvent(eventC2, List.of(eventA1, eventB1, eventC1));
        final EventDescriptor eventD2 = new EventDescriptor(randomHash(random), nodeD, 2);
        builder.addEvent(eventD2, List.of(eventA1, eventB1, eventC1, eventD1));

        assertEquals(2, window.getTheoreticalAdvancementScore(List.of(eventA1, eventB1, eventC1)));
        assertEquals(2, window.addEventAndGetAdvancementScore(eventA2));

        // This should have been enough to advance the snapshot window by 1.
        final Tipset snapshot2 = window.getSnapshot();
        assertNotSame(snapshot1, snapshot2);

        // D should have a bully score of 1, all others a score of 0.
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(1, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(1, window.getBullyScore());

        // Create another batch of events where D is bullied.
        final EventDescriptor eventA3 = new EventDescriptor(randomHash(random), nodeA, 3);
        builder.addEvent(eventA3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptor eventB3 = new EventDescriptor(randomHash(random), nodeB, 3);
        builder.addEvent(eventB3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptor eventC3 = new EventDescriptor(randomHash(random), nodeC, 3);
        builder.addEvent(eventC3, List.of(eventA2, eventB2, eventC2));
        final EventDescriptor eventD3 = new EventDescriptor(randomHash(random), nodeD, 3);
        builder.addEvent(eventD3, List.of(eventA2, eventB2, eventC2, eventD2));

        assertEquals(2, window.getTheoreticalAdvancementScore(List.of(eventA2, eventB2, eventC2)));
        assertEquals(2, window.addEventAndGetAdvancementScore(eventA3));

        final Tipset snapshot3 = window.getSnapshot();
        assertNotSame(snapshot2, snapshot3);

        // D should have a bully score of 2, all others a score of 0.
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(2, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(2, window.getBullyScore());

        // Create a bach of events that don't bully D. Let's all bully C, because C is a jerk.
        final EventDescriptor eventA4 = new EventDescriptor(randomHash(random), nodeA, 4);
        builder.addEvent(eventA4, List.of(eventA3, eventB3, eventD3));
        final EventDescriptor eventB4 = new EventDescriptor(randomHash(random), nodeB, 4);
        builder.addEvent(eventB4, List.of(eventA3, eventB3, eventD3));
        final EventDescriptor eventC4 = new EventDescriptor(randomHash(random), nodeC, 4);
        builder.addEvent(eventC4, List.of(eventA3, eventB3, eventC3, eventD3));
        final EventDescriptor eventD4 = new EventDescriptor(randomHash(random), nodeD, 4);
        builder.addEvent(eventD4, List.of(eventA3, eventB3, eventD3));

        assertEquals(2, window.getTheoreticalAdvancementScore(List.of(eventA3, eventB3, eventD3)));
        assertEquals(2, window.addEventAndGetAdvancementScore(eventA4));

        final Tipset snapshot4 = window.getSnapshot();
        assertNotSame(snapshot3, snapshot4);

        // Now, all nodes should have a bully score of 0 except for C, which should have a score of 1.
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(1, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(1, window.getBullyScore());

        // Stop bullying C. D stops creating events.
        final EventDescriptor eventA5 = new EventDescriptor(randomHash(random), nodeA, 5);
        builder.addEvent(eventA5, List.of(eventA4, eventB4, eventC4, eventD4));
        final EventDescriptor eventB5 = new EventDescriptor(randomHash(random), nodeB, 5);
        builder.addEvent(eventB5, List.of(eventA4, eventB4, eventC4, eventD4));
        final EventDescriptor eventC5 = new EventDescriptor(randomHash(random), nodeC, 5);
        builder.addEvent(eventC5, List.of(eventA4, eventB4, eventC4, eventD4));

        assertEquals(3, window.getTheoreticalAdvancementScore(List.of(eventA4, eventB4, eventC4, eventD4)));
        assertEquals(3, window.addEventAndGetAdvancementScore(eventA5));

        final Tipset snapshot5 = window.getSnapshot();
        assertNotSame(snapshot4, snapshot5);

        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(0, window.getBullyScore());

        // D still is not creating events. Since there is no legal event from D to use as a parent, this doesn't
        // count as bullying.
        final EventDescriptor eventA6 = new EventDescriptor(randomHash(random), nodeA, 6);
        builder.addEvent(eventA6, List.of(eventA5, eventB5, eventC5));
        final EventDescriptor eventB6 = new EventDescriptor(randomHash(random), nodeB, 6);
        builder.addEvent(eventB6, List.of(eventA5, eventB5, eventC5));
        final EventDescriptor eventC6 = new EventDescriptor(randomHash(random), nodeC, 6);
        builder.addEvent(eventC6, List.of(eventA5, eventB5, eventC5));

        assertEquals(2, window.getTheoreticalAdvancementScore(List.of(eventA5, eventB5, eventC5)));
        assertEquals(2, window.addEventAndGetAdvancementScore(eventA6));

        final Tipset snapshot6 = window.getSnapshot();
        assertNotSame(snapshot5, snapshot6);

        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(0, window.getBullyScore());

        // Rinse and repeat.
        final EventDescriptor eventA7 = new EventDescriptor(randomHash(random), nodeA, 7);
        builder.addEvent(eventA7, List.of(eventA6, eventB6, eventC6));
        final EventDescriptor eventB7 = new EventDescriptor(randomHash(random), nodeB, 7);
        builder.addEvent(eventB7, List.of(eventA6, eventB6, eventC6));
        final EventDescriptor eventC7 = new EventDescriptor(randomHash(random), nodeC, 7);
        builder.addEvent(eventC7, List.of(eventA6, eventB6, eventC6));

        assertEquals(2, window.getTheoreticalAdvancementScore(List.of(eventA6, eventB6, eventC6)));
        assertEquals(2, window.addEventAndGetAdvancementScore(eventA7));

        final Tipset snapshot7 = window.getSnapshot();
        assertNotSame(snapshot6, snapshot7);

        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeA.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeB.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeC.id()));
        assertEquals(0, window.getBullyScoreForNodeIndex((int) nodeD.id()));
        assertEquals(0, window.getBullyScore());
    }
}
