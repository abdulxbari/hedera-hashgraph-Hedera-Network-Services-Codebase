package com.hedera.services.utils.accessors;

import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.SwirldTransaction;

import java.util.function.Function;

public interface CryptographicSigs {
	/* --- These methods are needed by the signature management infrastructure to take a transaction through the
    expandSignatures -> handleTransaction lifecycle --- */
	byte[] getTxnBytes();

	PubKeyToSigBytes getPkToSigsFn();

	SwirldTransaction getPlatformTxn();

	/* --- Used to track entities with keys linked to a transaction --- */
	void setLinkedRefs(LinkedRefs linkedRefs);

	LinkedRefs getLinkedRefs();

	/* --- Used to track the results of creating signatures for all linked keys --- */
	void setExpandedSigStatus(ResponseCodeEnum status);

	ResponseCodeEnum getExpandedSigStatus();

	/* --- Used to track the results of validating derived signatures --- */
	void setSigMeta(RationalizedSigMeta sigMeta);

	RationalizedSigMeta getSigMeta();

	Function<byte[], TransactionSignature> getRationalizedPkToCryptoSigFn();
}
