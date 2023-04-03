/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.config.api.observable;

import edu.umd.cs.findbugs.annotations.NonNull;

public class Sample {

    public void sample1(final ObservableConfiguration configuration) {
        final ObservableProperty<Integer> fooProperty = configuration.getObservableProperty("foo", Integer.class);
        System.out.println("Current value: " + fooProperty.getValue()); //Might throw NoSuchElementException
    }

    public void sample2(final ObservableConfiguration configuration) {
        final ObservableProperty<Integer> fooProperty = configuration.getObservableProperty("foo", Integer.class);
        System.out.println("Current value or -1 -> " + fooProperty.getValue(-1));
    }

    public void sample3(final ObservableConfiguration configuration) {
        final ObservableProperty<Integer> fooProperty = configuration.getObservableProperty("foo", Integer.class);
        fooProperty.isSet(value -> System.out.println("Current value is " + value));
    }

    public void sample4(final ObservableConfiguration configuration) {
        final ObservableProperty<Integer> fooProperty = configuration.getObservableProperty("foo", Integer.class);
        fooProperty.isSet(value -> System.out.println("Current value is " + value))
                .orElse(() -> System.out.println("Property is not set"));
    }

    public void sample5(final ObservableConfiguration configuration) {
        final ObservableProperty<Integer> fooProperty = configuration.getObservableProperty("foo", Integer.class);
        fooProperty.observe(new PropertyObserver<Integer>() {
            @Override
            public void onStart(@NonNull final ObservableProperty<Integer> property,
                    @NonNull final Observation observation) {
                property.isSet(value -> System.out.println("Current value is " + value))
                        .orElse(() -> System.out.println("Property is not set"));
            }

            @Override
            public void onUpdate(@NonNull final ObservableProperty<Integer> property) {
                property.isSet(value -> System.out.println("Current value is " + value))
                        .orElse(() -> System.out.println("Property is not set"));
            }

            @Override
            public void onError(@NonNull final Throwable throwable) {

            }
        });
    }

}
