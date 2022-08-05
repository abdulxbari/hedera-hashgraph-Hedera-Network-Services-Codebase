package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.codec.DecoderException;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.validSentinel;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

public class CryptoUpdateAccessor extends SignedTxnAccessor {
	private final CryptoUpdateTransactionBody op;
	private final GlobalDynamicProperties properties;
	private final OptionValidator validator;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final NodeInfo nodeInfo;

	public CryptoUpdateAccessor(final byte[] signedTxnWrapperBytes,
			@Nullable final Transaction txn,
			final GlobalDynamicProperties properties,
			final OptionValidator validator,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final NodeInfo nodeInfo) throws InvalidProtocolBufferException {
		super(signedTxnWrapperBytes, txn);
		op = getTxn().getCryptoUpdateAccount();
		this.properties = properties;
		this.validator = validator;
		this.accounts = accounts;
		this.nodeInfo = nodeInfo;
		setCryptoUpdateUsageMeta();
	}

	public AccountID accountIdToUpdate(){
		return unaliased(op.getAccountIDToUpdate()).toGrpcAccountId();
	}

	public boolean hasExpirationTime(){
		return op.hasExpirationTime();
	}

	public Timestamp getExpirationTime(){
		return op.getExpirationTime();
	}

	@Override
	public boolean supportsPrecheck() {
		return true;
	}

	@Override
	public ResponseCodeEnum doPrecheck() {
		return validateSyntax();
	}

	private ResponseCodeEnum validateSyntax() {
		var memoValidity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (memoValidity != OK) {
			return memoValidity;
		}

		if (op.hasKey()) {
			try {
				JKey fcKey = JKey.mapKey(op.getKey());
				/* Note that an empty key is never valid. */
				if (!fcKey.isValid()) {
					return INVALID_ADMIN_KEY;
				}
			} catch (DecoderException e) {
				return BAD_ENCODING;
			}
		}

		if (op.hasAutoRenewPeriod() && !validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.hasProxyAccountID()
				&& !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
			return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
		}

		final var stakedIdCase = op.getStakedIdCase().name();
		final var electsStakingId = hasStakedId(stakedIdCase);
		if (!properties.isStakingEnabled() && (electsStakingId || op.hasDeclineReward())) {
			return STAKING_NOT_ENABLED;
		}
		if (electsStakingId) {
			if (validSentinel(stakedIdCase, op.getStakedAccountId(), op.getStakedNodeId())) {
				return OK;
			} else if (!validator.isValidStakedId(
					stakedIdCase,
					op.getStakedAccountId(),
					op.getStakedNodeId(),
					accounts.get(),
					nodeInfo)) {
				return INVALID_STAKING_ID;
			}
		}
		return OK;
	}

	private void setCryptoUpdateUsageMeta() {
		final var cryptoUpdateMeta =
				new CryptoUpdateMeta(
						op,
						getTxnId().getTransactionValidStart().getSeconds());
		getSpanMapAccessor().setCryptoUpdate(this, cryptoUpdateMeta);
	}

	public HederaAccountCustomizer asCustomizer() {
		HederaAccountCustomizer customizer = new HederaAccountCustomizer();

		if (op.hasKey()) {
			/* Note that {@code this.validate(TransactionBody)} will have rejected any txn with an invalid key. */
			var fcKey = asFcKeyUnchecked(op.getKey());
			customizer.key(fcKey);
		}
		if (op.hasExpirationTime()) {
			customizer.expiry(op.getExpirationTime().getSeconds());
		}
		if (op.hasReceiverSigRequiredWrapper()) {
			customizer.isReceiverSigRequired(op.getReceiverSigRequiredWrapper().getValue());
		} else if (op.getReceiverSigRequired()) {
			customizer.isReceiverSigRequired(true);
		}
		if (op.hasAutoRenewPeriod()) {
			customizer.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
		}
		if (op.hasMemo()) {
			customizer.memo(op.getMemo().getValue());
		}
		if (op.hasMaxAutomaticTokenAssociations()) {
			customizer.maxAutomaticAssociations(op.getMaxAutomaticTokenAssociations().getValue());
		}
		if (op.hasDeclineReward()) {
			customizer.isDeclinedReward(op.getDeclineReward().getValue());
		}
		if (hasStakedId(op.getStakedIdCase().name())) {
			customizer.customizeStakedId(
					op.getStakedIdCase().name(), op.getStakedAccountId(), op.getStakedNodeId());
		}

		return customizer;
	}
}
