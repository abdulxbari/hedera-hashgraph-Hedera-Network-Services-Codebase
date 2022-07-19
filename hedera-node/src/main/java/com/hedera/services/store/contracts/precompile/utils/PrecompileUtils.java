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
package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILE_MIRROR_ENTITY_ID;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/** Utility class for Precompile contracts */
public class PrecompileUtils {
    public static void addContractCallResultToRecord(
            final long gasRequirement,
            final ExpirableTxnRecord.Builder childRecord,
            final Bytes result,
            final Optional<ResponseCodeEnum> errorStatus,
            final boolean shouldExportPrecompileResults,
            final boolean traceabilityOn,
            final Address senderAddress,
            long remainingGas,
            long value,
            byte[] inputData) {
        if (shouldExportPrecompileResults) {
            final var evmFnResult =
                    new EvmFnResult(
                            HTS_PRECOMPILE_MIRROR_ENTITY_ID,
                            result != null ? result.toArrayUnsafe() : EvmFnResult.EMPTY,
                            errorStatus.map(ResponseCodeEnum::name).orElse(null),
                            EvmFnResult.EMPTY,
                            gasRequirement,
                            Collections.emptyList(),
                            Collections.emptyList(),
                            EvmFnResult.EMPTY,
                            Collections.emptyMap(),
                            traceabilityOn ? remainingGas : 0L,
                            traceabilityOn ? value : 0L,
                            traceabilityOn ? inputData : EvmFnResult.EMPTY,
                            EntityId.fromAddress(senderAddress));
            childRecord.setContractCallResult(evmFnResult);
        }
    }

    private PrecompileUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
