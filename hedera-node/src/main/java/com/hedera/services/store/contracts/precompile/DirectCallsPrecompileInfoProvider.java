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
package com.hedera.services.store.contracts.precompile;

import com.hedera.services.ledger.accounts.ContractAliases;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public record DirectCallsPrecompileInfoProvider(PrecompileMessage precompileMessage)
        implements PrecompileInfoProvider {
    @Override
    public Wei getValue() {
        return precompileMessage.getValue();
    }

    @Override
    public long getRemainingGas() {
        return precompileMessage.getGasRemaining();
    }

    @Override
    public boolean isDirectTokenCall() {
        return true;
    }

    @Override
    public long getTimestamp() {
        return precompileMessage.getConsensusTime();
    }

    @Override
    public Address getSenderAddress() {
        return precompileMessage.getSenderAddress();
    }

    @Override
    public Bytes getInputData() {
        return precompileMessage.getInputData();
    }

    @Override
    public void setState(MessageFrame.State state) {
        precompileMessage.setState(state);
    }

    @Override
    public void setRevertReason(Bytes revertReason) {
        precompileMessage.setRevertReason(revertReason);
    }

    @Override
    public void addLog(Log log) {
        precompileMessage.addLog(log);
    }

    @Nullable
    @Override
    public ContractAliases aliases() {
        return null;
    }
}
