package com.hedera.node.app.service.mono.state.virtual.utils;

import java.io.IOException;

public interface CheckedIntConsumer {
	void accept(int t) throws IOException;
}
