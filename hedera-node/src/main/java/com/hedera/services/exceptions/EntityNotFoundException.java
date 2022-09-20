package com.hedera.services.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class EntityNotFoundException extends InvalidTransactionException {
    public EntityNotFoundException(ResponseCodeEnum responseCode) {
        super(responseCode);
    }
}
