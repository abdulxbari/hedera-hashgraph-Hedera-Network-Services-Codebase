/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    @Test
    void testSpi() {
        // when
        final ContractService service = ContractService.getInstance();

        // then
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                ContractServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type " + ContractServiceImpl.class.getName());
    }

    @Test
    void registersExpectedSchema() {
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        final var subject = ContractService.getInstance();

        subject.registerSchemas(registry);
        verify(registry).register(schemaCaptor.capture());

        final var schema = schemaCaptor.getValue();

        final var statesToCreate = schema.statesToCreate();
        assertEquals(1, statesToCreate.size());
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(ContractServiceImpl.STORAGE_KEY, iter.next());
    }
}
