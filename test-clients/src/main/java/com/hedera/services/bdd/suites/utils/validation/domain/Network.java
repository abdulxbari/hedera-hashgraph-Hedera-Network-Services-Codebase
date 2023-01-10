/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.utils.validation.domain;

import com.hedera.services.bdd.suites.utils.validation.ValidationScenarios;
import java.util.List;

public class Network {
    public static final String SCENARIO_PAYER_NAME = "scenarioPayer";

    private static final long DEFAULT_INITIAL_HBARS = 25;
    private static final long DEFAULT_NODE = 3;

    long bootstrap;
    long defaultNode = DEFAULT_NODE;
    long defaultFeeInHbars = ValidationScenarios.FEE_TO_OFFER;
    long defaultNodePaymentInTinybars = 100;
    long ensureScenarioPayerHbars = DEFAULT_INITIAL_HBARS;

    Long scenarioPayer;

    List<Node> nodes;
    Scenarios scenarios;

    public long getDefaultNode() {
        return defaultNode;
    }

    public void setDefaultNode(long defaultNode) {
        this.defaultNode = defaultNode;
    }

    public long getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(long bootstrap) {
        this.bootstrap = bootstrap;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public Scenarios getScenarios() {
        return scenarios;
    }

    public void setScenarios(Scenarios scenarios) {
        this.scenarios = scenarios;
    }

    public Long getScenarioPayer() {
        return scenarioPayer;
    }

    public void setScenarioPayer(Long scenarioPayer) {
        this.scenarioPayer = scenarioPayer;
    }

    public long getEnsureScenarioPayerHbars() {
        return ensureScenarioPayerHbars;
    }

    public void setEnsureScenarioPayerHbars(long ensureScenarioPayerHbars) {
        this.ensureScenarioPayerHbars = ensureScenarioPayerHbars;
    }

    public long getDefaultFeeInHbars() {
        return defaultFeeInHbars;
    }

    public void setDefaultFeeInHbars(long defaultFeeInHbars) {
        this.defaultFeeInHbars = defaultFeeInHbars;
    }

    public long getDefaultNodePaymentInTinybars() {
        return defaultNodePaymentInTinybars;
    }

    public void setDefaultNodePaymentInTinybars(long defaultNodePaymentInTinybars) {
        this.defaultNodePaymentInTinybars = defaultNodePaymentInTinybars;
    }
}
