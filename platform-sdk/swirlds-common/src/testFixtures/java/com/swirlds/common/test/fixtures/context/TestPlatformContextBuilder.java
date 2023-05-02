/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.fixtures.context;

import static com.swirlds.common.config.ConfigUtils.scanAndRegisterAllConfigTypes;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A simple builder to create a {@link PlatformContext} for unit tests.
 */
public final class TestPlatformContextBuilder {

    private static final Metrics defaultMetrics = new NoOpMetrics();
    private static final Configuration defaultConfig =
            scanAndRegisterAllConfigTypes(ConfigurationBuilder.create()).build();
    private static final Cryptography defaultCryptography = CryptographyHolder.get();

    private Configuration configuration;
    private Metrics metrics;
    private Cryptography cryptography;

    private TestPlatformContextBuilder() {}

    /**
     * Creates a new builder instance
     *
     * @return a new instance
     */
    public static TestPlatformContextBuilder create() {
        return new TestPlatformContextBuilder();
    }

    /**
     * Set the {@link Configuration} to use. If null or not set, uses a default configuration.
     *
     * @param testConfigBuilder the config builder
     * @return the builder instance
     */
    public TestPlatformContextBuilder withConfiguration(@Nullable final Configuration configuration) {
        this.configuration = configuration;
        return this;
    }

    /**
     * Set the {@link Metrics} to use. If null or not set, uses a default metrics instance.
     *
     * @param metrics the metrics to use
     */
    public TestPlatformContextBuilder withMetrics(@Nullable final Metrics metrics) {
        this.metrics = metrics;
        return this;
    }

    /**
     * Set the {@link Cryptography} to use. If null or not set, uses a default cryptography instance.
     *
     * @param cryptography the cryptography to use
     */
    public TestPlatformContextBuilder withCryptography(@Nullable final Cryptography cryptography) {
        this.cryptography = cryptography;
        return this;
    }

    /**
     * Returns a new {@link PlatformContext} based on this builder
     *
     * @return a new {@link PlatformContext}
     */
    public PlatformContext build() {
        if (configuration == null) {
            configuration = defaultConfig;
        }
        if (metrics == null) {
            metrics = defaultMetrics; // TODO replace this with NoOp Metrics
        }
        if (this.cryptography == null) {
            this.cryptography = defaultCryptography;
        }

        return new PlatformContext() {
            @Override
            public Configuration getConfiguration() {
                return configuration;
            }

            @Override
            public Cryptography getCryptography() {
                return cryptography;
            }

            @Override
            public Metrics getMetrics() {
                return metrics;
            }
        };
    }
}
