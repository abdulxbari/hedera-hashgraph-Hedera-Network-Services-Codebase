package com.hedera.services.bdd.spec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class HapiStakingSpec extends HapiSpec {
    private static final ThreadPoolExecutor THREAD_POOL =
            new ThreadPoolExecutor(0, 10_000, 250, MILLISECONDS, new SynchronousQueue<>());

    static final Logger log = LogManager.getLogger(HapiStakingSpec.class);


    /*The first List<HapiSpecOperation> represents everything that should run in the first period
    after staking is enabled; the second List<HapiSpecOperation> is everything that should run in
    the second period after staking is enabled; and so on*/
    List<List<HapiSpecOperation>> periodNOperations = new ArrayList<>();
    HapiStakingSpec skipPeriod(){
        periodNOperations.add(List.of());
        return this;
    }
    HapiStakingSpec thenRun(HapiSpecOperation... operations){
        periodNOperations.add(List.of(operations));
        return this;
    }
    List<HapiSpecOperation> operationsFor(int periodIndex){
        return periodNOperations.get(periodIndex);
    }

    @Override
    public void run() {
        if (!init()) {
            return;
        }
        for (int i = 0; i < periodNOperations.size(); i++) {
            exec(operationsFor(i));
            waitUntilStartOfNextStakingPeriod(70000L);
        }
    }

    public static ThreadPoolExecutor getCommonThreadPool() {
        return THREAD_POOL;
    }
}
