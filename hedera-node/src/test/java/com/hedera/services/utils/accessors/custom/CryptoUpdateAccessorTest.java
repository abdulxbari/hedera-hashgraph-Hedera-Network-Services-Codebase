package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.memo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoUpdateAccessorTest {

	@Mock private AccessorFactory accessorFactory;
	@Mock private GlobalDynamicProperties dynamicProperties;
	@Mock private OptionValidator validator;
	@Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock private NodeInfo nodeInfo;

	CryptoUpdateAccessor subject;

	@Test
	void setCryptoUpdateUsageMetaWorks() throws InvalidProtocolBufferException {
		final var txn = signedCryptoUpdateTxn();
		subject= new CryptoUpdateAccessor(txn.toByteArray(), txn, dynamicProperties, validator, () -> accounts, nodeInfo);
		final var spanMapAccessor = subject.getSpanMapAccessor();

		final var expandedMeta = spanMapAccessor.getCryptoUpdateMeta(subject);

		assertEquals(100, expandedMeta.getKeyBytesUsed());
		assertEquals(197, expandedMeta.getMsgBytesUsed());
		assertEquals(now, expandedMeta.getEffectiveNow());
		assertEquals(now + autoRenewPeriod, expandedMeta.getExpiry());
		assertEquals(memo.getBytes().length, expandedMeta.getMemoSize());
		assertEquals(25, expandedMeta.getMaxAutomaticAssociations());
		assertTrue(expandedMeta.hasProxy());
	}

	private Transaction signedCryptoUpdateTxn() {
		return buildTransactionFrom(cryptoUpdateOp());
	}

	private TransactionBody cryptoUpdateOp() {
		final var op =
				CryptoUpdateTransactionBody.newBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(now + autoRenewPeriod))
						.setProxyAccountID(autoRenewAccount)
						.setMemo(StringValue.newBuilder().setValue(memo))
						.setMaxAutomaticTokenAssociations(Int32Value.of(25))
						.setKey(adminKey);
		return TransactionBody.newBuilder()
				.setTransactionID(
						TransactionID.newBuilder()
								.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
				.setCryptoUpdateAccount(op)
				.build();
	}

	private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private static final long autoRenewPeriod = 1_234_567L;
	private static final AccountID autoRenewAccount = IdUtils.asAccount("0.0.75231");
	private static final long now = 1_234_567L;
}
