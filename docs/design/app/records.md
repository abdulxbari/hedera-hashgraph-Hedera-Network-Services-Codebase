# Records

One of the _essential_ responsibilities of a consensus node is to produce the blockchain. In Hedera, we refer
to a block in the blockchain as a "Record File" and the blockchain as a "Record Stream". Each consensus node
produces its _own_ blockchain, and mirror nodes validate the chains from multiple consensus nodes to verify their
correctness and integrity (in the future with BLS, nodes will collaborate on signing a blockchain, making
the task much simpler for mirror nodes).

The record file is defined in the [protobuf schema](https://github.com/hashgraph/hedera-protobufs/blob/main/streams/record_stream_file.proto).
Each record file has the hash of the previous record file, its own running hash, and all the data that makes up the
running hash. That data consists of `Transaction`s and `TransactionRecord`s. There is also some metadata such as the
consensus time for the start of the record file.

![record stream](./images/record-stream.png)

## Design

The design for the production of record files must fulfill the following requirements:
 - Production of records and record files must not slow down TPS of the system
 - Record files must take < 2 seconds to produce
 - `TransactionRecord`s are stored both in state (for queries) and in record files
 - 


**NEXT: [States](states.md)**
