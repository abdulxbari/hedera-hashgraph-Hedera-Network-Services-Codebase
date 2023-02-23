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

package com.hedera.node.app.fees;

import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertFeeDataFromDtoToProto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertHederaFunctionalityFromProtoToDto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertTimestampFromProtoToDto;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProviderImpl;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.HashMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Adapter for {@link UsageBasedFeeCalculator} to be used in {@link QueryWorkflow}. This class is
 * currently calling mono-service code and will be replaced with a new implementation as per design.
 */
@Singleton
public class MonoFeeAccumulator implements FeeAccumulator {
    private final UsageBasedFeeCalculator feeCalculator;
    private final PricesAndFeesProvider pricesAndFeesProvider;
    private final Supplier<StateView> stateView;

    @Inject
    public MonoFeeAccumulator(
            final UsageBasedFeeCalculator feeCalculator,
            final FeeResourcesLoaderImpl feeResourcesLoader,
            final Supplier<StateView> stateView) {
        this.feeCalculator = feeCalculator;
        this.pricesAndFeesProvider = new PricesAndFeesProviderImpl(feeResourcesLoader);
        this.stateView = stateView;
    }

    @Override
    public FeeObject computePayment(HederaFunctionality functionality, Query query, Timestamp now) {
        final var usagePrices = convertFeeDataFromDtoToProto(pricesAndFeesProvider.defaultPricesGiven(
                convertHederaFunctionalityFromProtoToDto(functionality), convertTimestampFromProtoToDto(now)));
        return feeCalculator.computePayment(query, usagePrices, stateView.get(), now, new HashMap<>());
    }
}
