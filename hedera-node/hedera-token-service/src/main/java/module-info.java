module com.hedera.node.app.service.token {
    exports com.hedera.node.app.service.token;

    uses com.hedera.node.app.service.token.TokenService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
}
