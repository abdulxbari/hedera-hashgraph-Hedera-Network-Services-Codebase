package com.hedera.node.app.spi.records;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.*;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom builder for SingleTransactionRecord.
 */
public class SingleTransactionRecordBuilder {
    // base transaction data
    private Transaction transaction;
    private Bytes transactionBytes;
    // fields needed for TransactionRecord
    private Timestamp consensusTimestamp;
    private long transactionFee;
    private ContractFunctionResult contractCallResult;
    private ContractFunctionResult contractCreateResult;
    private TransferList transferList;
    private List<TokenTransferList> tokenTransferLists;
    private ScheduleID scheduleRef;
    private List<AssessedCustomFee> assessedCustomFees;
    private List<TokenAssociation> automaticTokenAssociations;
    private Timestamp parentConsensusTimestamp;
    private Bytes alias;
    private Bytes ethereumHash;
    private List<AccountAmount> paidStakingRewards;
    private OneOf<TransactionRecord.EntropyOneOfType> entropy;
    private Bytes evmAddress;
    // fields needed for TransactionReceipt
    private ResponseCodeEnum status;
    private AccountID accountID;
    private FileID fileID;
    private ContractID contractID;
    private ExchangeRateSet exchangeRate;
    private TopicID topicID;
    private long topicSequenceNumber;
    private Bytes topicRunningHash;
    private long topicRunningHashVersion;
    private TokenID tokenID;
    private long newTotalSupply;
    private ScheduleID scheduleID;
    private TransactionID scheduledTransactionID;
    private List<Long> serialNumbers;
    // Sidecar data, booleans are the migration flag
    public final List<AbstractMap.SimpleEntry<ContractStateChanges,Boolean>> contractStateChanges = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractActions,Boolean>> contractActions = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractBytecode,Boolean>> contractBytecodes = new ArrayList<>();

    @SuppressWarnings("DataFlowIssue")
    public SingleTransactionRecord build() {
        // compute transaction hash: TODO could pass in if we have it calculated else where
        final byte[] transactionBytes = new byte[(int)this.transactionBytes.length()];
        this.transactionBytes.getBytes(0,transactionBytes);
        final Bytes transactionHash = Bytes.wrap(new Hash(transactionBytes).getValue());
        // create body one of
        OneOf<TransactionRecord.BodyOneOfType> body = new OneOf<>(TransactionRecord.BodyOneOfType.UNSET, null);
        if (contractCallResult != null) body = new OneOf<>(TransactionRecord.BodyOneOfType.CONTRACT_CALL_RESULT, contractCallResult);
        if (contractCreateResult != null) body = new OneOf<>(TransactionRecord.BodyOneOfType.CONTRACT_CREATE_RESULT, contractCreateResult);
        // create list of sidecar records
        List<TransactionSidecarRecord> transactionSidecarRecords = new ArrayList<>();
        contractStateChanges.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(
                                TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES,
                                pair.getKey())))
                        .forEach(transactionSidecarRecords::add);
        contractActions.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(
                                TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS,
                                pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractBytecodes.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(
                                TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE,
                                pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        // build
        return new SingleTransactionRecord(
                transaction,
                new TransactionRecord(
                        new TransactionReceipt(
                                status,
                                accountID,
                                fileID,
                                contractID,
                                exchangeRate,
                                topicID,
                                topicSequenceNumber,
                                topicRunningHash,
                                topicRunningHashVersion,
                                tokenID,
                                newTotalSupply,
                                scheduleID,
                                scheduledTransactionID,
                                serialNumbers
                        ),
                        transactionHash,
                        consensusTimestamp,
                        transaction.body().transactionID(),
                        transaction.body().memo(),
                        transactionFee,
                        body,
                        transferList,
                        tokenTransferLists,
                        scheduleRef,
                        assessedCustomFees,
                        automaticTokenAssociations,
                        parentConsensusTimestamp,
                        alias,
                        ethereumHash,
                        paidStakingRewards,
                        entropy,
                        evmAddress),
                transactionSidecarRecords
        );
    }
    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data
    public SingleTransactionRecordBuilder transaction(Transaction transaction, Bytes transactionBytes) {
        this.transaction = transaction;
        this.transactionBytes = transactionBytes;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord

    public SingleTransactionRecordBuilder consensusTimestamp(Timestamp consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    public SingleTransactionRecordBuilder transactionFee(long transactionFee) {
        this.transactionFee = transactionFee;
        return this;
    }

    public SingleTransactionRecordBuilder contractCallResult(ContractFunctionResult contractCallResult) {
        this.contractCallResult = contractCallResult;
        return this;
    }

    public SingleTransactionRecordBuilder contractCreateResult(ContractFunctionResult contractCreateResult) {
        this.contractCreateResult = contractCreateResult;
        return this;
    }

    public SingleTransactionRecordBuilder transferList(TransferList transferList) {
        this.transferList = transferList;
        return this;
    }

    public SingleTransactionRecordBuilder tokenTransferLists(List<TokenTransferList> tokenTransferLists) {
        this.tokenTransferLists = tokenTransferLists;
        return this;
    }

    public SingleTransactionRecordBuilder scheduleRef(ScheduleID scheduleRef) {
        this.scheduleRef = scheduleRef;
        return this;
    }

    public SingleTransactionRecordBuilder assessedCustomFees(List<AssessedCustomFee> assessedCustomFees) {
        this.assessedCustomFees = assessedCustomFees;
        return this;
    }

    public SingleTransactionRecordBuilder automaticTokenAssociations(List<TokenAssociation> automaticTokenAssociations) {
        this.automaticTokenAssociations = automaticTokenAssociations;
        return this;
    }

    public SingleTransactionRecordBuilder parentConsensusTimestamp(Timestamp parentConsensusTimestamp) {
        this.parentConsensusTimestamp = parentConsensusTimestamp;
        return this;
    }

    public SingleTransactionRecordBuilder alias(Bytes alias) {
        this.alias = alias;
        return this;
    }

    public SingleTransactionRecordBuilder ethereumHash(Bytes ethereumHash) {
        this.ethereumHash = ethereumHash;
        return this;
    }

    public SingleTransactionRecordBuilder paidStakingRewards(List<AccountAmount> paidStakingRewards) {
        this.paidStakingRewards = paidStakingRewards;
        return this;
    }

    public SingleTransactionRecordBuilder entropy(OneOf<TransactionRecord.EntropyOneOfType> entropy) {
        this.entropy = entropy;
        return this;
    }

    public SingleTransactionRecordBuilder evmAddress(Bytes evmAddress) {
        this.evmAddress = evmAddress;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    public SingleTransactionRecordBuilder status(ResponseCodeEnum status) {
        this.status = status;
        return this;
    }

    public SingleTransactionRecordBuilder accountID(AccountID accountID) {
        this.accountID = accountID;
        return this;
    }

    public SingleTransactionRecordBuilder fileID(FileID fileID) {
        this.fileID = fileID;
        return this;
    }

    public SingleTransactionRecordBuilder contractID(ContractID contractID) {
        this.contractID = contractID;
        return this;
    }

    public SingleTransactionRecordBuilder exchangeRate(ExchangeRateSet exchangeRate) {
        this.exchangeRate = exchangeRate;
        return this;
    }

    public SingleTransactionRecordBuilder topicID(TopicID topicID) {
        this.topicID = topicID;
        return this;
    }

    public SingleTransactionRecordBuilder topicSequenceNumber(long topicSequenceNumber) {
        this.topicSequenceNumber = topicSequenceNumber;
        return this;
    }

    public SingleTransactionRecordBuilder topicRunningHash(Bytes topicRunningHash) {
        this.topicRunningHash = topicRunningHash;
        return this;
    }

    public SingleTransactionRecordBuilder topicRunningHashVersion(long topicRunningHashVersion) {
        this.topicRunningHashVersion = topicRunningHashVersion;
        return this;
    }

    public SingleTransactionRecordBuilder tokenID(TokenID tokenID) {
        this.tokenID = tokenID;
        return this;
    }

    public SingleTransactionRecordBuilder newTotalSupply(long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        return this;
    }

    public SingleTransactionRecordBuilder scheduleID(ScheduleID scheduleID) {
        this.scheduleID = scheduleID;
        return this;
    }

    public SingleTransactionRecordBuilder scheduledTransactionID(TransactionID scheduledTransactionID) {
        this.scheduledTransactionID = scheduledTransactionID;
        return this;
    }

    public SingleTransactionRecordBuilder serialNumbers(List<Long> serialNumbers) {
        this.serialNumbers = serialNumbers;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag
    public SingleTransactionRecordBuilder addContractStateChanges(ContractStateChanges contractStateChanges, boolean isMigration) {
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    public SingleTransactionRecordBuilder addContractAction(ContractActions contractAction, boolean isMigration) {
        contractActions.add(new AbstractMap.SimpleEntry<>(contractAction, isMigration));
        return this;
    }

    public SingleTransactionRecordBuilder addContractBytecode(ContractBytecode contractBytecode, boolean isMigration) {
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }
}
