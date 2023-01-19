package com.hedera.node.app.service.token.record;

import com.hedera.node.app.spi.record.RecordBuilder;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link RecordBuilder} used when a token has been created.
 */
public interface CreateTokenRecordHistorian extends RecordBuilder {
    @NonNull
    CreateTokenRecordHistorian tokenAlias(@NonNull byte[] value);

    @NonNull
    CreateTokenRecordHistorian receiptCreatedTokenID(@NonNull TokenID tokenID);
}
