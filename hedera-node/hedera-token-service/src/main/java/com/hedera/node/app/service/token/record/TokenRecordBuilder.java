package com.hedera.node.app.service.token.record;

import com.hedera.node.app.spi.record.RecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface TokenRecordBuilder extends RecordBuilder {
    @NonNull
    TokenRecordBuilder receiptNewTotalSupply(long value);

    @NonNull
    TokenRecordBuilder receiptTotalIssued(long value);

    @NonNull
    TokenRecordBuilder receiptSerialNumbers(long value);
}

