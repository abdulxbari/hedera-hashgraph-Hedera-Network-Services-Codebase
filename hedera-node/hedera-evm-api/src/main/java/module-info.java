/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.services.evm {
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires tuweni.bytes;
    requires tuweni.units;
    requires javax.inject;
    requires com.swirlds.common;
    requires com.github.benmanes.caffeine;

    exports com.hedera.services.evm.contracts.execution;
    exports com.hedera.services.evm.store.contracts;
    exports com.hedera.services.evm.store.models;
    exports com.hedera.services.evm.store.contracts.utils;
    exports com.hedera.services.evm;
}
