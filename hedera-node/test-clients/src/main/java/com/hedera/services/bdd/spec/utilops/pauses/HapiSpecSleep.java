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

package com.hedera.services.bdd.spec.utilops.pauses;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiSpecSleep extends UtilOp {
    static final Logger log = LogManager.getLogger(HapiSpecSleep.class);

    private long timeMs;

    public HapiSpecSleep(long timeMs) {
        this.timeMs = timeMs;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        log.info("Sleeping for " + timeMs + "ms now...");
        Thread.sleep(timeMs);
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("timeMs", timeMs).toString();
    }
}