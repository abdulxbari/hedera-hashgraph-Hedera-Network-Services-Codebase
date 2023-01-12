package com.hedera.node.app.service.token.record;

import com.hedera.node.app.spi.record.RecordBuilder;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link RecordBuilder} used when a token has been created.
 */
public interface CreateTokenRecordBuilder extends RecordBuilder {
    @NonNull
    CreateTokenRecordBuilder tokenAlias(@NonNull byte[] value);

    @NonNull
    CreateTokenRecordBuilder receiptCreatedTokenID(@NonNull TokenID tokenID);
}
