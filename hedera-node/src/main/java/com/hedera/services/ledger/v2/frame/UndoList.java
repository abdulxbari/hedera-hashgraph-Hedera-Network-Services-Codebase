package com.hedera.services.ledger.v2.frame;

import java.util.List;

public interface UndoList {
    void beginFrame();
    void commitFrame();
    List<ReversibleChange> changesInFrame();
}
