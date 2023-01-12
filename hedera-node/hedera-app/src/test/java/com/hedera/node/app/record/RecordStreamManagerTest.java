package com.hedera.node.app.record;

public class RecordStreamManagerTest {
    // Constructor
    //  - dir does not exist
    //  - dir is not writable
    //  - dir is a file

    // Submit
    //  - submit a null record
    //  - submit a record that happened BEFORE the most recent (last) consensus time
    //  - submit a record that is within 2 seconds of the lastConsensusTime (no record file)
    //  - submit a record that is exactly 2 seconds of the lastConsensusTime (...?)
    //  - submit a record that is longer than 2 seconds of the lastConsensus time (submit records)
    //  - race condition between submit and onConsensusTimeAdvanced (or single threaded??)

    // OnConsensusTimeAdvanced
    //  - consensusTime <= lastConsensusTime (shouldn't ever happen... EVER!!!)

}
