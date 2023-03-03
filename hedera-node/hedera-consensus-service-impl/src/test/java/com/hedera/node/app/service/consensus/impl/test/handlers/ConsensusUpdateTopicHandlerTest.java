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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusUpdateTopicHandlerTest {
    private final ConsensusServiceConfig consensusConfig = new ConsensusServiceConfig(1234L, 5678);

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private ConsensusUpdateTopicRecordBuilder recordBuilder;

    private ConsensusUpdateTopicHandler subject = new ConsensusUpdateTopicHandler();

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusUpdateTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Handle method not implemented")
    void handleNotImplemented() {
        final var op = transactionBody.getConsensusUpdateTopic();
        // expect:
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.handle(handleContext, op, consensusConfig, recordBuilder));
    }
}