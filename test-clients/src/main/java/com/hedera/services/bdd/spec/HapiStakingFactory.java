package com.hedera.services.bdd.spec;

import java.util.ArrayList;
import java.util.List;

public class HapiStakingFactory {
    /*The first List<HapiSpecOperation> represents everything that should run in the first period
    after staking is enabled; the second List<HapiSpecOperation> is everything that should run in
    the second period after staking is enabled; and so on*/
    List<List<HapiSpecOperation>> periodNOperations = new ArrayList<>();
    String specId();
    HapiStakingFactory skipPeriod(){
        periodNOperations.add(List.of());
        return this;
    }
    HapiStakingFactory thenRun(HapiSpecOperation... operations){
        periodNOperations.add(List.of(operations));
        return this;
    }
    List<HapiSpecOperation> operationsFor(int periodIndex){
        return periodNOperations.get(periodIndex);
    }
}
