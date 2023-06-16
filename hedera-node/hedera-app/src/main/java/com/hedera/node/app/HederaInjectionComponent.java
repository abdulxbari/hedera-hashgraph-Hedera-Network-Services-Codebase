/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.authorization.AuthorizerInjectionModule;
import com.hedera.node.app.components.IngestInjectionComponent;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.config.ConfigModule;
import com.hedera.node.app.config.GenesisUsage;
import com.hedera.node.app.fees.FeesInjectionModule;
import com.hedera.node.app.info.InfoInjectionModule;
import com.hedera.node.app.metrics.MetricsInjectionModule;
import com.hedera.node.app.records.RecordsInjectionModule;
import com.hedera.node.app.service.mono.LegacyMonoInjectionModule;
import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.services.ServicesInjectionModule;
import com.hedera.node.app.solvency.SolvencyInjectionModule;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.HederaStateInjectionModule;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.throttle.ThrottleInjectionModule;
import com.hedera.node.app.workflows.WorkflowsInjectionModule;
import com.hedera.node.app.workflows.prehandle.AdaptedMonoEventExpansion;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import dagger.BindsInstance;
import dagger.Component;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * The infrastructure used to implement the platform contract for a Hedera Services node. This is needed for adding
 * dagger subcomponents. Currently, it extends {@link com.hedera.node.app.service.mono.ServicesApp}. But, in the future
 * this class will be cleaned up to not have multiple module dependencies
 */
@Singleton
@Component(
        modules = {
            LegacyMonoInjectionModule.class,
            ServicesInjectionModule.class,
            WorkflowsInjectionModule.class,
            HederaStateInjectionModule.class,
            FeesInjectionModule.class,
            MetricsInjectionModule.class,
            AuthorizerInjectionModule.class,
            InfoInjectionModule.class,
            RecordsInjectionModule.class,
            ThrottleInjectionModule.class,
            SolvencyInjectionModule.class,
            ConfigModule.class
        })
public interface HederaInjectionComponent extends ServicesApp {
    /* Needed by ServicesState */
    Provider<QueryInjectionComponent.Factory> queryComponentFactory();

    Provider<IngestInjectionComponent.Factory> ingestComponentFactory();

    WorkingStateAccessor workingStateAccessor();

    AdaptedMonoEventExpansion adaptedMonoEventExpansion();

    NonAtomicReference<HederaState> mutableState();

    RecordCache recordCache();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder initTrigger(InitTrigger initTrigger);

        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder initialHash(Hash initialHash);

        @BindsInstance
        Builder platform(@NonNull Platform platform);

        @BindsInstance
        Builder consoleCreator(StateModule.ConsoleCreator consoleCreator);

        @BindsInstance
        Builder selfId(@NodeSelfId final AccountID selfId);

        @BindsInstance
        Builder staticAccountMemo(@StaticAccountMemo String accountMemo);

        @BindsInstance
        Builder bootstrapProps(@BootstrapProps PropertySource bootstrapProps);

        @BindsInstance
        Builder maxSignedTxnSize(@MaxSignedTxnSize final int maxSignedTxnSize);

        /**
         * @deprecated we need to define the correct workflow to define that genesis is used
         */
        @Deprecated
        @BindsInstance
        Builder genesisUsage(@GenesisUsage final boolean genesisUsage);

        HederaInjectionComponent build();
    }
}
