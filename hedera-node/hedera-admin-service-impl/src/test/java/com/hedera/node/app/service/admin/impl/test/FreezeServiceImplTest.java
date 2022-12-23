/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.admin.impl.test;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.FreezeServiceImpl;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class FreezeServiceImplTest {

	@Test
	void testSpi() {
		// when
		final FreezeService service = FreezeService.getInstance();

		// then
		Assertions.assertNotNull(service, "We must always receive an instance");
		Assertions.assertEquals(
				FreezeServiceImpl.class,
				service.getClass(),
				"We must always receive an instance of type " + FreezeServiceImpl.class.getName());
		Assertions.assertEquals(
				FreezeService.class.getSimpleName(),
				service.getServiceName(),
				"Service must have a reasonable name");
	}

	@Test
	void testServiceSpi() {
		// given
		final Set<Service> services = ServiceFactory.loadServices();

		// then
		Assertions.assertNotNull(services, "We must always receive an instance");
		Assertions.assertEquals(1, services.size(), "The module only provides 1 service");
		Assertions.assertEquals(
				FreezeServiceImpl.class,
				services.iterator().next().getClass(),
				"We must always receive an instance of type " + FreezeServiceImpl.class.getName());
	}


}
