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
package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {
    @Mock private States states;
    @Mock private HederaAccountNumbers numbers;
    @Mock private HederaFileNumbers fileNumbers;
    @Mock private AccountKeyLookup keyLookup;
    public PreHandleContext preHandleCtx;

    @BeforeEach
    void setUp() {
        preHandleCtx = new PreHandleContext(numbers, fileNumbers, keyLookup);
    }

    @Test
    void testsSpi() {
        final ScheduleService service = ScheduleService.getInstance();
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                ScheduleServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type "
                        + ScheduleServiceImpl.class.getName());
        Assertions.assertEquals("ScheduleService", service.getServiceName());
    }
}
