module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires org.apache.commons.lang3;
    requires com.google.common;
    requires com.hedera.node.app.service.mono;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.spi;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires dagger;
    requires javax.inject;
    requires com.hedera.pbj.runtime;
    requires com.github.spotbugs.annotations;
    requires transitive com.hedera.node.hapi;
    requires org.apache.logging.log4j;
    requires com.swirlds.config;
    requires com.hedera.node.config;
    requires org.slf4j;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.serdes;
    exports com.hedera.node.app.service.token.impl;
    exports com.hedera.node.app.service.token.impl.records to
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.validators;
}
