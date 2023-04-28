/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.DoubleAccumulator;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.utility.CommonUtils;

/**
 * Encapsulates statistics for a virtual map.
 */
public class VirtualMapStatistics {

    public static final String STAT_CATEGORY = "virtual-map";
    private static final double DEFAULT_HALF_LIFE = 5;

    /** Metric name prefix for all virtual map metric names */
    private static final String VMAP_PREFIX = "vmap_";
    /** Prefix for all metrics related to virtual map queries */
    private static final String QUERIES_PREFIX = "queries_";
    /** Prefix for all lifecycle related metric names */
    private static final String LIFECYCLE_PREFIX = "lifecycle_";

    /** Virtual Map name */
    private final String label;

    /** Virtual map entities - total number */
    private LongGauge size;

    /** Virtual map entities - adds / s */
    private LongAccumulator addedEntities;
    /** Virtual map entities - updates / s */
    private LongAccumulator updatedEntities;
    /** Virtual map entities - deletes / s */
    private LongAccumulator removedEntities;
    /** Virtual map entities - reads / s */
    private LongAccumulator readEntities;

    /** Number of virtual root copies in the pipeline */
    private IntegerGauge pipelineSize;
    /** The number of virtual root node copies in virtual pipeline flush backlog */
    private IntegerGauge flushBacklogSize;
    /** Flush backpressure duration, ms */
    private IntegerAccumulator flushBackpressureMs;
    /** The average time to merge virtual map copy to the next copy, ms */
    private DoubleAccumulator mergeDurationMs;
    /** The average time to flush virtual map copy to disk (to data source), ms */
    private DoubleAccumulator flushDurationMs;
    /** The number of virtual root node copy flushes to data source */
    private Counter flushCount;
    /** The average time to hash virtual map copy, ms */
    private DoubleAccumulator hashDurationMs;

    private static CountPerSecond buildCountPerSecond(
            final Metrics metrics, final String name, final String description) {
        return new CountPerSecond(metrics, new CountPerSecond.Config(STAT_CATEGORY, name).withDescription(description));
    }

    private static LongAccumulator buildLongAccumulator(
            final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new LongAccumulator.Config(STAT_CATEGORY, name)
                .withInitialValue(0)
                .withAccumulator(Long::sum)
                .withDescription(description));
    }

    private static IntegerAccumulator buildIntegerAccumulator(
            final Metrics metrics, final String name, final String description) {
        return metrics.getOrCreate(new IntegerAccumulator.Config(STAT_CATEGORY, name)
                .withInitialValue(0)
                .withAccumulator(Integer::sum)
                .withDescription(description));
    }

    /**
     * Create a new statistics instance for a virtual map family.
     *
     * @param label
     * 		the label for the virtual map
     * @throws IllegalArgumentException
     * 		if {@code label} is {@code null}
     */
    public VirtualMapStatistics(final String label) {
        CommonUtils.throwArgNull(label, "label");
        this.label = label;
    }

    /**
     * Register all statistics with a registry.
     *
     * @param metrics
     * 		reference to the metrics system
     * @throws IllegalArgumentException
     * 		if {@code metrics} is {@code null}
     */
    public void registerMetrics(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");

        // Generic
        size = metrics.getOrCreate(new LongGauge.Config(STAT_CATEGORY, VMAP_PREFIX + "size_" + label)
                .withDescription("Virtual map size, " + label));

        // Queries
        addedEntities = buildLongAccumulator(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "addedEntities_" + label,
                "Added virtual map entities, " + label);
        updatedEntities = buildLongAccumulator(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "updatedEntities_" + label,
                "Updated virtual map entities, " + label + ", per second");
        removedEntities = buildLongAccumulator(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "removedEntities_" + label,
                "Removed virtual map entities, " + label + ", per second");
        readEntities = buildLongAccumulator(
                metrics,
                VMAP_PREFIX + QUERIES_PREFIX + "readEntities_" + label,
                "Read virtual map entities, " + label + ", per second");

        // Lifecycle
        pipelineSize = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "pipelineSize_" + label)
                        .withDescription("Virtual pipeline size, " + label));
        flushBacklogSize = metrics.getOrCreate(
                new IntegerGauge.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushBacklogSize_" + label)
                        .withDescription("Virtual pipeline flush backlog size" + label));
        flushBackpressureMs = buildIntegerAccumulator(
                metrics,
                VMAP_PREFIX + LIFECYCLE_PREFIX + "flushBackpressureMs_" + label,
                "Virtual pipeline flush backpressure, " + label + ", ms");
        mergeDurationMs = metrics.getOrCreate(
                new DoubleAccumulator.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "mergeDurationMs_" + label)
                        .withFormat(FORMAT_10_2)
                        .withDescription("Virtual root copy merge duration, " + label + ", ms"));
        flushDurationMs = metrics.getOrCreate(
                new DoubleAccumulator.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushDurationMs_" + label)
                        .withFormat(FORMAT_10_2)
                        .withDescription("Virtual root copy flush duration, " + label + ", ms"));
        flushCount = metrics.getOrCreate(
                new Counter.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "flushCount_" + label)
                        .withDescription("Virtual root copy flush count, " + label));
        hashDurationMs = metrics.getOrCreate(
                new DoubleAccumulator.Config(STAT_CATEGORY, VMAP_PREFIX + LIFECYCLE_PREFIX + "hashDurationMs_" + label)
                        .withFormat(FORMAT_10_2)
                        .withDescription("Virtual root copy hash duration, " + label + ", ms"));
    }

    /**
     * Update the size statistic for the virtual map.
     *
     * @param size the value to set
     */
    public void setSize(final long size) {
        if (this.size != null) {
            this.size.set(size);
        }
    }

    /**
     * Increments {@link #addedEntities} stat by 1.
     */
    public void countAddedEntities() {
        if (addedEntities != null) {
            addedEntities.update(1);
        }
    }

    /**
     * Increments {@link #updatedEntities} stat by 1.
     */
    public void countUpdatedEntities() {
        if (updatedEntities != null) {
            updatedEntities.update(1);
        }
    }

    /**
     * Increments {@link #removedEntities} stat by 1.
     */
    public void countRemovedEntities() {
        if (removedEntities != null) {
            removedEntities.update(1);
        }
    }

    /**
     * Increments {@link #readEntities} stat by 1.
     */
    public void countReadEntities() {
        if (readEntities != null) {
            readEntities.update(1);
        }
    }

    public void resetEntityCounters() {
        if (addedEntities != null) {
            addedEntities.reset();
        }
        if (updatedEntities != null) {
            updatedEntities.reset();
        }
        if (removedEntities != null) {
            removedEntities.reset();
        }
        if (readEntities != null) {
            readEntities.reset();
        }
    }

    /**
     * Updates {@link #pipelineSize} stat to the given value.
     *
     * @param value the value to set
     */
    public void setPipelineSize(final int value) {
        if (this.pipelineSize != null) {
            this.pipelineSize.set(value);
        }
    }

    /**
     * Updates {@link #flushBacklogSize} stat.
     *
     * @param size flush backlog size
     */
    public void recordFlushBacklogSize(final int size) {
        if (flushBacklogSize != null) {
            flushBacklogSize.set(size);
        }
    }

    /**
     * Updates {@link #flushBackpressureMs} stat.
     *
     * @param backpressureMs flush backpressure, ms
     */
    public void recordFlushBackpressureMs(final int backpressureMs) {
        if (flushBackpressureMs != null) {
            flushBackpressureMs.update(backpressureMs);
        }
    }

    /**
     * Record a virtual root copy is merged, and merge duration is as specified.
     *
     * @param mergeDurationMs merge duration, ms
     */
    public void recordMerge(final double mergeDurationMs) {
        if (this.mergeDurationMs != null) {
            this.mergeDurationMs.update(mergeDurationMs);
        }
    }

    /**
     * Record a virtual root copy is flushed, and flush duration is as specified.
     *
     * @param flushDurationMs flush duration, ms
     */
    public void recordFlush(final double flushDurationMs) {
        if (this.flushCount != null) {
            this.flushCount.increment();
        }
        if (this.flushDurationMs != null) {
            this.flushDurationMs.update(flushDurationMs);
        }
    }

    /**
     * Record a virtual root copy is hashed, and hash duration is as specified.
     *
     * @param hashDurationMs flush duration, ms
     */
    public void recordHash(final double hashDurationMs) {
        if (this.hashDurationMs != null) {
            this.hashDurationMs.update(hashDurationMs);
        }
    }
}
