module com.hedera.node.app.service.schedule {
    exports com.hedera.node.app.service.schedule;

    uses com.hedera.node.app.service.schedule.ScheduleService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
}
