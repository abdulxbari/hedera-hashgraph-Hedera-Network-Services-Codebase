package com.hedera.node.app.spi.record;

import com.hederahashgraph.api.proto.java.TransactionRecord;

public interface RecordBuilder {
    void addChildRecord(RecordBuilder hist);
    void populate(TransactionRecord.Builder builder);
}
