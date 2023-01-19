package com.hedera.node.app.service.contract.record;

import com.hedera.node.app.spi.record.RecordBuilder;

public interface SmartContractRecordBuilder extends RecordBuilder {
    void addChildRecord(RecordBuilder builder);
}
