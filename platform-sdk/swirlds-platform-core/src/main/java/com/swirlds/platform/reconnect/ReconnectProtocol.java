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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import java.io.IOException;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implements the reconnect protocol over a bidirectional network */
public class ReconnectProtocol implements Protocol {

    private static final Logger logger = LogManager.getLogger(ReconnectProtocol.class);

    private final NodeId peerId;
    private final ReconnectThrottle teacherThrottle;
    private final Supplier<ReservedSignedState> lastCompleteSignedState;
    private final int reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final SignedStateValidator validator;
    private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;
    private final ThreadManager threadManager;
    private final FallenBehindManager fallenBehindManager;
    private ReservedSignedState teacherState;

    /**
     * @param threadManager           responsible for creating and managing threads
     * @param peerId                  the ID of the peer we are communicating with
     * @param teacherThrottle         restricts reconnects as a teacher
     * @param lastCompleteSignedState provides the latest completely signed state
     * @param reconnectSocketTimeout  the socket timeout to use when executing a reconnect
     * @param reconnectMetrics        tracks reconnect metrics
     * @param reconnectController     controls reconnecting as a learner
     * @param fallenBehindManager     maintains this node's behind status
     */
    public ReconnectProtocol(
            final ThreadManager threadManager,
            final NodeId peerId,
            final ReconnectThrottle teacherThrottle,
            final Supplier<ReservedSignedState> lastCompleteSignedState,
            final int reconnectSocketTimeout,
            final ReconnectMetrics reconnectMetrics,
            final ReconnectController reconnectController,
            final SignedStateValidator validator,
            final FallenBehindManager fallenBehindManager) {
        this.threadManager = threadManager;
        this.peerId = peerId;
        this.teacherThrottle = teacherThrottle;
        this.lastCompleteSignedState = lastCompleteSignedState;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.reconnectMetrics = reconnectMetrics;
        this.reconnectController = reconnectController;
        this.validator = validator;
        this.fallenBehindManager = fallenBehindManager;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldInitiate() {
        // if this neighbor has not told me I have fallen behind, I will not reconnect with him
        if (!fallenBehindManager.shouldReconnectFrom(peerId)) {
            return false;
        }

        // if a permit is acquired, it will be released by either initiateFailed or runProtocol
        final boolean acquiredPermit = reconnectController.acquireLearnerPermit();
        if (acquiredPermit) {
            initiatedBy = InitiatedBy.SELF;
        }
        return acquiredPermit;
    }

    /** {@inheritDoc} */
    @Override
    public void initiateFailed() {
        reconnectController.cancelLearnerPermit();
        initiatedBy = InitiatedBy.NO_ONE;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldAccept() {
        // we should not be the teacher if we have fallen behind
        if (fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        // Check if we have a state that is legal to send to a learner.
        teacherState = lastCompleteSignedState.get();

        if (teacherState.isNull()) {
            logger.info(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} due to lack of a fully signed state",
                    peerId);
            return false;
        }

        if (!teacherState.get().getState().isInitialized()) {
            teacherState.close();
            teacherState = null;
            logger.warn(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} " + "due to lack of an initialized signed state.",
                    peerId);
            return false;
        } else if (!teacherState.get().isComplete()) {
            // this is only possible if signed state manager violates its contractual obligations
            teacherState.close();
            teacherState = null;
            logger.error(
                    RECONNECT.getMarker(),
                    "Rejecting reconnect request from node {} due to lack of a fully signed state."
                            + " The signed state manager attempted to provide a state that was not"
                            + " fully signed, which should not be possible.",
                    peerId);
            return false;
        }

        // Check if a reconnect with the learner is permitted by the throttle.
        final boolean reconnectPermittedByThrottle = teacherThrottle.initiateReconnect(peerId);
        if (reconnectPermittedByThrottle) {
            initiatedBy = InitiatedBy.PEER;
            return true;
        } else {
            teacherState.close();
            teacherState = null;
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void acceptFailed() {
        teacherState.close();
        teacherState = null;
        teacherThrottle.reconnectAttemptFinished();
    }

    /** {@inheritDoc} */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        // if both nodes fall behind, it makes no sense to reconnect with each other
        // also, it would not be clear who the teacher and who the learner is
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            switch (initiatedBy) {
                case PEER -> teacher(connection);
                case SELF -> learner(connection);
                default -> throw new NetworkProtocolException(
                        "runProtocol() called but it is unclear who the teacher and who the learner is");
            }
        } finally {
            initiatedBy = InitiatedBy.NO_ONE;
        }
    }

    /**
     * Perform reconnect as the learner.
     *
     * @param connection the connection to use for the reconnect
     */
    private void learner(final Connection connection) throws InterruptedException {
        reconnectController.setStateValidator(validator);
        reconnectController.provideLearnerConnection(connection);
    }

    /**
     * Perform reconnect as the teacher.
     *
     * @param connection the connection to use for the reconnect
     */
    private void teacher(final Connection connection) {

        try (final ReservedSignedState state = teacherState) {
            new ReconnectTeacher(
                            threadManager,
                            connection,
                            reconnectSocketTimeout,
                            connection.getSelfId(),
                            connection.getOtherId(),
                            state.get().getRound(),
                            fallenBehindManager::hasFallenBehind,
                            reconnectMetrics)
                    .execute(state.get());
        } finally {
            teacherThrottle.reconnectAttemptFinished();
            teacherState = null;
        }
    }

    private enum InitiatedBy {
        NO_ONE,
        SELF,
        PEER
    }
}
