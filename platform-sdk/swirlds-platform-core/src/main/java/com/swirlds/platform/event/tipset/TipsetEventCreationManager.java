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

package com.swirlds.platform.event.tipset;

import static com.swirlds.base.state.LifecyclePhase.NOT_STARTED;
import static com.swirlds.base.state.LifecyclePhase.STARTED;
import static com.swirlds.base.state.LifecyclePhase.STOPPED;

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Manages the creation of events.
 */
public class TipsetEventCreationManager implements Lifecycle { // TODO test

    private LifecyclePhase lifecyclePhase = NOT_STARTED;
    private final NodeId selfId;
    private final TipsetEventCreator eventCreator;

    private final MultiQueueThread workQueue;
    private final BlockingQueueInserter<EventImpl> eventInserter;
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;
    private final Consumer<GossipEvent> newEventHandler;
    private final RateLimiter rateLimiter;

    public TipsetEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier,
            @NonNull final Consumer<GossipEvent> newEventHandler) {

        this.selfId = Objects.requireNonNull(selfId);
        this.newEventHandler = Objects.requireNonNull(newEventHandler);

        eventCreator = new TipsetEventCreator(
                platformContext, time, random, signer, addressBook, selfId, softwareVersion, transactionSupplier);

        workQueue = new MultiQueueThreadConfiguration(threadManager)
                .setThreadName("event-creator")
                .setCapacity(1024) // TODO setting for capacity
                .setMaxBufferSize(1024) // TODO setting
                .addHandler(EventImpl.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .setIdleCallback(this::maybeCreateEvent)
                .setBufferHandledCallback(this::maybeCreateEvent)
                .setWaitForWorkDuration(Duration.ZERO) // TODO setting
                .build();

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        final double maxCreationRate = eventCreationConfig.maxCreationRate();
        if (maxCreationRate > 0) {
            rateLimiter = new RateLimiter(time, eventCreationConfig.maxCreationRate());
        } else {
            // No brakes!
            rateLimiter = null;
        }

        eventInserter = workQueue.getInserter(EventImpl.class);
        minimumGenerationNonAncientInserter = workQueue.getInserter(Long.class);
    }

    /**
     * Add an event from the event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) throws InterruptedException {
        if (event.getHashedData().getCreatorId().equals(selfId)) {
            // TODO this behavior needs to be different at startup time if not at genesis
            return;
        }
        eventInserter.put(event);
    }

    /**
     * Upgate the minimum generation non-ancient
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Pass an event into the event creator.
     *
     * @param event the event to pass
     */
    private void handleEvent(@NonNull final EventImpl event) {
        eventCreator.registerEvent(event);
    }

    /**
     * Pass a new minimum generation non-ancient into the event creator.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    private void handleMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        eventCreator.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }

    /**
     * Create a new event if it is legal to do so.
     */
    private void maybeCreateEvent() {
        // TODO API for event creation rules, e.g. stop creating events if falling behind, etc.

        if (rateLimiter != null && !rateLimiter.request()) {
            // We have created a self event too recently
            return;
        }

        final GossipEvent event = eventCreator.createNewEvent();
        if (event != null) {
            newEventHandler.accept(event);

            if (rateLimiter != null) {
                rateLimiter.trigger();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(NOT_STARTED);
        lifecyclePhase = STARTED;
        workQueue.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(STARTED);
        lifecyclePhase = STOPPED;
        workQueue.stop();
    }
}