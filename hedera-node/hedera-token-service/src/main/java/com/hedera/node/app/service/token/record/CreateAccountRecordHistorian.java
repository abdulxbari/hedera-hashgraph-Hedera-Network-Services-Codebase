package com.hedera.node.app.service.token.record;

import com.hedera.node.app.spi.record.RecordBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link RecordBuilder} that records information about new accounts that are created.
 */
public interface CreateAccountRecordHistorian extends RecordBuilder {
    /**
     * In the record of a CryptoCreate transaction triggered by a user transaction with a
     * (previously unused) alias, the new account's alias.
     */
    @NonNull
    CreateAccountRecordHistorian accountAlias(@NonNull byte[] value);

    /**
     * In the receipt of a CryptoCreate, the id of the newly created account
     */
    @NonNull
    CreateAccountRecordHistorian receiptCreatedAccountID(@NonNull AccountID accountID);
}
