package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CryptoApproveAllowanceAccessor extends SignedTxnAccessor {
	private final AccountStore accountStore;
	private final ApproveAllowanceChecks allowanceChecks;
	private final StateView workingView;
	private final CryptoApproveAllowanceTransactionBody body;

	public CryptoApproveAllowanceAccessor(final byte[] signedTxnWrapperBytes,
			@Nullable final Transaction transaction,
			final ApproveAllowanceChecks allowanceChecks,
			final StateView workingView,
			final AccountStore accountStore) throws InvalidProtocolBufferException {
		super(signedTxnWrapperBytes, transaction);
		this.body = getTxn().getCryptoApproveAllowance();
		this.accountStore = accountStore;
		this.allowanceChecks = allowanceChecks;
		this.workingView = workingView;
	}

	@Override
	public boolean supportsPrecheck() {
		return true;
	}

	@Override
	public ResponseCodeEnum doPrecheck() {
		return validateSyntax();
	}

	public List<CryptoAllowance> cryptoAllowances(){
		return body.getCryptoAllowancesList();
	}
	public List<TokenAllowance> tokenAllowances(){
		return body.getTokenAllowancesList();
	}
	public List<NftAllowance> nftAllowances(){
		return body.getNftAllowancesList();
	}

	private ResponseCodeEnum validateSyntax() {
		final AccountID payer = getPayer();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

		return allowanceChecks.allowancesValidation(
				body.getCryptoAllowancesList(),
				body.getTokenAllowancesList(),
				body.getNftAllowancesList(),
				payerAccount,
				workingView);
	}
}
