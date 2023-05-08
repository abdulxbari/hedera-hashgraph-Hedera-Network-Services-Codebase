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

package com.hedera.node.app.service.util.impl.records;

import com.hedera.node.app.service.util.records.PrngRecordBuilder;
import com.hedera.node.app.spi.records.UniversalRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Record builder for updating prng bytes or number.
 */
public class UtilPrngRecordBuilder extends UniversalRecordBuilder<PrngRecordBuilder> implements PrngRecordBuilder {
    /**
     * The generated random number, when range is provided in {@code UtilPrngTransactionBody}.
     */
    private Integer randomNumber;
    /**
     * The generated random bytes, when range is not provided in {@code UtilPrngTransactionBody}.
     */
    private Bytes randomBytes;

    /**
     * {@inheritDoc}
     */
    @Override
    protected UtilPrngRecordBuilder self() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PrngRecordBuilder setPrngNumber(int num) {
        this.randomNumber = num;
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PrngRecordBuilder setPrngBytes(Bytes prngBytes) {
        this.randomBytes = prngBytes;
        return self();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getPrngNumber() {
        return randomNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bytes getPrngBytes() {
        return randomBytes;
    }
}
