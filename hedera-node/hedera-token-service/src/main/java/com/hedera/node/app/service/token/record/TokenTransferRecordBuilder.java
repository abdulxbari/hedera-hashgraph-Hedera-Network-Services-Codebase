package com.hedera.node.app.service.token.record;

import com.hedera.node.app.spi.record.RecordBuilder;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

/**
 * A {@link RecordBuilder} used for recording token transfer information, suitable for all transactions
 * involving token transfers.
 */
public interface TokenTransferRecordBuilder extends RecordBuilder {
    @NonNull
    TokenTransferRecordBuilder tokenTransferList(@NonNull TokenTransferList value);

    @NonNull
    TokenTransferRecordBuilder assessedCustomFees(@NonNull List<AssessedCustomFee> values);

    @NonNull
    TokenTransferRecordBuilder automaticTokenAssociations(@NonNull List<TokenAssociation> values);
}
