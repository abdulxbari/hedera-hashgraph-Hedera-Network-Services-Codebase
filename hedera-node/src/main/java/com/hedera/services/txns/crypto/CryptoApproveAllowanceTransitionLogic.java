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
package com.hedera.services.txns.crypto;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.utils.accessors.custom.CryptoApproveAllowanceAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoApproveAllowance transaction, and the
 * conditions under which such logic is syntactically correct.
 */
public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final ApproveAllowanceLogic approveAllowanceLogic;

    @Inject
    public CryptoApproveAllowanceTransitionLogic(
            final TransactionContext txnCtx,
            final ApproveAllowanceLogic approveAllowanceLogic) {
        this.txnCtx = txnCtx;
        this.approveAllowanceLogic = approveAllowanceLogic;
    }

    @Override
    public void doStateTransition() {
        /* --- Extract gRPC --- */
        final var accessor = (CryptoApproveAllowanceAccessor) txnCtx.specializedAccessor();
        AccountID payer = txnCtx.activePayer();
        final var cryptoAllowances = accessor.cryptoAllowances();
        final var tokenAllowances = accessor.tokenAllowances();
        final var nftAllowances = accessor.nftAllowances();

        approveAllowanceLogic.approveAllowance(
                cryptoAllowances,
                tokenAllowances,
                nftAllowances,
                payer);

        txnCtx.setStatus(SUCCESS);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoApproveAllowance;
    }
}
