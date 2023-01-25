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
package com.hedera.services.bdd.suites;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiStakingSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.hedera.services.bdd.suites.HapiSuite.FinalOutcome.SUITE_FAILED;
import static com.hedera.services.bdd.suites.HapiSuite.FinalOutcome.SUITE_PASSED;

public abstract class HapiStakingSuite extends HapiSuite {
    private List<HapiStakingSpec> finalStakingSpecs = new ArrayList<>();
    public List<HapiStakingSpec> getFinalStakingSpecs() {
        return finalStakingSpecs;
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public FinalOutcome runSuiteAsync() {
        return runSuite(HapiStakingSuite::runConcurrentSpecs);
    }

    public abstract List<HapiStakingSpec> getStakingSpecsInSuite();


    @SuppressWarnings("java:S2629")
    private FinalOutcome runSuite(final Consumer<List<HapiStakingSpec>> runner) {
        if (!getDeferResultsSummary()) {
            getResultsLogger().info(STARTING_SUITE, name());
        }

        List<HapiStakingSpec> specs = getStakingSpecsInSuite();
        specs.forEach(spec -> spec.setSuitePrefix(name()));
        runner.accept(specs);
        finalStakingSpecs = specs;
        summarizeResults(getResultsLogger());
        if (tearDownClientsAfter) {
            HapiApiClients.tearDown();
        }
        return finalOutcomeFor(finalStakingSpecs);
    }

    protected FinalOutcome finalOutcomeFor(final List<? extends HapiSpec> completedSpecs) {
        return completedSpecs.stream().allMatch(HapiStakingSpec::ok) ? SUITE_PASSED : SUITE_FAILED;
    }
}
