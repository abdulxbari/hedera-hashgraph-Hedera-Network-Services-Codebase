import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;

module com.hedera.node.app.service.networkadmin.impl {
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.networkadmin;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.networkadmin.FreezeService with
            FreezeServiceImpl;
    provides NetworkService with
            com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;

    exports com.hedera.node.app.service.networkadmin.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.networkadmin.impl.test;
    exports com.hedera.node.app.service.networkadmin.impl.handlers;
    exports com.hedera.node.app.service.networkadmin.impl.codec;
    exports com.hedera.node.app.service.networkadmin.impl.serdes to
            com.hedera.node.app.service.networkadmin.impl.test;
}
