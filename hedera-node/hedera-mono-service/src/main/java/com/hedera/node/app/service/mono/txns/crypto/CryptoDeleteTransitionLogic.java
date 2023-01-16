/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.exceptions.DeletedAccountException;
import com.hedera.node.app.service.mono.exceptions.MissingEntityException;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hedera.node.app.service.mono.txns.crypto.helpers.CryptoDeletionLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoDelete transaction, and the conditions
 * under which such logic is syntactically correct. (It is possible that the <i>semantics</i> of the
 * transaction will still be wrong; for example, if the target account expired before this
 * transaction reached consensus.)
 */
@Singleton
public class CryptoDeleteTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(CryptoDeleteTransitionLogic.class);

    private final CryptoDeletionLogic deletionLogic;
    private final TransactionContext txnCtx;

    @Inject
    public CryptoDeleteTransitionLogic(
        final CryptoDeletionLogic deletionLogic
            final TransactionContext txnCtx) {
        this.deletionLogic = deletionLogic;
        this.txnCtx = txnCtx;
    }

    @Override
    public void doStateTransition() {
            final var op = txnCtx.accessor().getTxn().getCryptoDelete();
            final var deleted = deletionLogic.performCryptoDeleteFor(op);

            txnCtx.recordBeneficiaryOfDeleted(deleted.getAccountNum(), deletionLogic.getLastBeneficiary().getAccountNum());
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoDelete;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(TransactionBody cryptoDeleteTxn) {
return deletionLogic.validate(cryptoDeleteTxn.getCryptoDelete());
    }
}
