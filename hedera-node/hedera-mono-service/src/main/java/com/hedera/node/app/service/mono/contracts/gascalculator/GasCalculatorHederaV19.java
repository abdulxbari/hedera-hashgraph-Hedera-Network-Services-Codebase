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

package com.hedera.node.app.service.mono.contracts.gascalculator;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProviderImpl;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

/**
 * Provides Hedera adapted gas cost lookups and calculations used during transaction processing.
 * Maps the gas costs of the Smart Contract Service including and after 0.19.0 release
 */
public class GasCalculatorHederaV19 extends LondonGasCalculator {

    private final GlobalDynamicProperties dynamicProperties;
    private final PricesAndFeesProvider pricesAndFeesProvider;

    @Inject
    public GasCalculatorHederaV19(
            final GlobalDynamicProperties dynamicProperties, final FeeResourcesLoaderImpl feeResourcesLoader) {
        this.dynamicProperties = dynamicProperties;
        this.pricesAndFeesProvider = new PricesAndFeesProviderImpl(feeResourcesLoader);
    }

    @Override
    public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreate) {
        return 0L;
    }

    @Override
    public long codeDepositGasCost(final int codeSize) {
        return 0L;
    }

    @Override
    public long logOperationGasCost(
            final MessageFrame frame, final long dataOffset, final long dataLength, final int numTopics) {
        final var gasCost = GasCalculatorHederaUtil.logOperationGasCost(
                pricesAndFeesProvider, frame, getLogStorageDuration(), dataOffset, dataLength, numTopics);
        return Math.max(super.logOperationGasCost(frame, dataOffset, dataLength, numTopics), gasCost);
    }

    long getLogStorageDuration() {
        return dynamicProperties.cacheRecordsTtl();
    }
}
