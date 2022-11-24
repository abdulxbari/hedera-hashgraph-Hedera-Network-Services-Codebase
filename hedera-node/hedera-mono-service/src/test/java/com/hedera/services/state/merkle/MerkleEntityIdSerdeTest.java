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
package com.hedera.services.state.merkle;

import com.hedera.node.app.service.mono.state.merkle.MerkleEntityId;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleEntityIdSerdeTest extends SelfSerializableDataTest<MerkleEntityId> {
    @Override
    protected Class<MerkleEntityId> getType() {
        return MerkleEntityId.class;
    }

    @Override
    protected MerkleEntityId getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextMerkleEntityId();
    }
}
