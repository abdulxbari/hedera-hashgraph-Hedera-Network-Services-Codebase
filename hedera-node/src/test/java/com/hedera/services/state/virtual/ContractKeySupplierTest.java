package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.api.Test;

import static com.hedera.services.state.virtual.ContractKey.RUNTIME_CONSTRUCTABLE_ID;
import static com.hedera.services.state.virtual.ContractKeySupplier.CLASS_ID;
import static com.hedera.services.state.virtual.ContractKeySupplier.CURRENT_VERSION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractKeySupplierTest {
	private ContractKeySupplier subject = new ContractKeySupplier();

	@Test
	void gettersWork() {
		assertEquals(CLASS_ID, subject.getClassId());
		assertEquals(CURRENT_VERSION, subject.getVersion());
	}

	@Test
	void delegatesAsExpected() {
		var contractKey = subject.get();

		assertEquals(RUNTIME_CONSTRUCTABLE_ID, contractKey.getClassId());
	}

	@Test
	void serdesAreNoop() {
		assertDoesNotThrow(() -> subject.deserialize(null, 1));
		assertDoesNotThrow(() -> subject.serialize(null));
	}
}
