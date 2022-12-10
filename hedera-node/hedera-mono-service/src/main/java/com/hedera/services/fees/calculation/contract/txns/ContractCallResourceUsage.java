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
package com.hedera.services.fees.calculation.contract.txns;

import com.hedera.node.app.hapi.utils.exception.InvalidTxBodyException;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContractCallResourceUsage implements TxnResourceUsageEstimator {
    private final SmartContractFeeBuilder usageEstimator;

    @Inject
    public ContractCallResourceUsage(SmartContractFeeBuilder usageEstimator) {
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasContractCall();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view)
            throws InvalidTxBodyException {
        return usageEstimator.getContractCallTxFeeMatrices(txn, sigUsage);
    }
}