package com.hedera.services.legacy.handler;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.TextFormat;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.PureValidation;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.builder.RequestBuilder.getTimestamp;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionRecord;

/**
 * Post-consensus execution of smart contract api calls
 */
@Singleton
public class SmartContractRequestHandler {
	private static final Logger log = LogManager.getLogger(SmartContractRequestHandler.class);

	private final Map<EntityId, Long> entityExpiries;

	private final HederaLedger ledger;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final HbarCentExchange exchange;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public SmartContractRequestHandler(
			HederaLedger ledger,
			HbarCentExchange exchange,
			Map<EntityId, Long> entityExpiries,
			GlobalDynamicProperties dynamicProperties,
			Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.ledger = ledger;
		this.accounts = accounts;
		this.exchange = exchange;
		this.entityExpiries = entityExpiries;
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * check if a contract with given contractId exists
	 *
	 * @param contractID
	 * 		the contract id to check for existence
	 * @return CONTRACT_DELETED if deleted, INVALID_CONTRACT_ID if doesn't exist, OK otherwise
	 */
	public ResponseCodeEnum validateContractExistence(ContractID contractID) {
		return PureValidation.queryableContractStatus(contractID, accounts.get());
	}

	/**
	 * System account deletes any contract. This simply marks the contract as deleted.
	 *
	 * @param txBody
	 * 		API request to delete the contract
	 * @param consensusTimestamp
	 * 		Platform consensus time
	 * @return Details of contract deletion result
	 */
	public TransactionRecord systemDelete(TransactionBody txBody, Instant consensusTimestamp) {
		SystemDeleteTransactionBody op = txBody.getSystemDelete();
		ContractID cid = op.getContractID();
		long newExpiry = op.getExpirationTime().getSeconds();
		TransactionReceipt receipt;
		receipt = updateDeleteFlag(cid, true);
		try {
			if (receipt.getStatus().equals(ResponseCodeEnum.SUCCESS)) {
				AccountID id = asAccount(cid);
				long oldExpiry = ledger.expiry(id);
				var entity = EntityId.fromGrpcContractId(cid);
				entityExpiries.put(entity, oldExpiry);
				HederaAccountCustomizer customizer = new HederaAccountCustomizer().expiry(newExpiry);
				ledger.customizePotentiallyDeleted(id, customizer);
			}
		} catch (Exception e) {
			log.warn("Unhandled exception in SystemDelete", e);
			log.debug("File System Exception {} tx= {}", () -> e, () -> TextFormat.shortDebugString(op));
			receipt = getTransactionReceipt(ResponseCodeEnum.FILE_SYSTEM_EXCEPTION, exchange.activeRates());
		}

		TransactionRecord.Builder transactionRecord =
				getTransactionRecord(txBody.getTransactionFee(), txBody.getMemo(),
						txBody.getTransactionID(), getTimestamp(consensusTimestamp), receipt);
		return transactionRecord.build();

	}

	/**
	 * System account undoes the deletion marker on a smart contract that has been deleted but
	 * not yet removed.
	 *
	 * @param txBody
	 * 		API reuest to undelete the contract
	 * @param consensusTimestamp
	 * 		Platform consensus time
	 * @return Details of contract undeletion result
	 */
	public TransactionRecord systemUndelete(TransactionBody txBody, Instant consensusTimestamp) {
		SystemUndeleteTransactionBody op = txBody.getSystemUndelete();
		ContractID cid = op.getContractID();
		var entity = EntityId.fromGrpcContractId(cid);
		TransactionReceipt receipt = getTransactionReceipt(SUCCESS, exchange.activeRates());

		long oldExpiry = 0;
		try {
			if (entityExpiries.containsKey(entity)) {
				oldExpiry = entityExpiries.get(entity);
			} else {
				receipt = getTransactionReceipt(INVALID_FILE_ID, exchange.activeRates());
			}
			if (oldExpiry > 0) {
				HederaAccountCustomizer customizer = new HederaAccountCustomizer().expiry(oldExpiry);
				ledger.customizePotentiallyDeleted(asAccount(cid), customizer);
			}
			if (receipt.getStatus() == SUCCESS) {
				try {
					receipt = updateDeleteFlag(cid, false);
				} catch (Exception e) {
					receipt = getTransactionReceipt(FAIL_INVALID, exchange.activeRates());
					if (log.isDebugEnabled()) {
						log.debug("systemUndelete exception: can't serialize or deserialize! tx=" + txBody, e);
					}
				}
			}
			entityExpiries.remove(entity);
		} catch (Exception e) {
			log.warn("Unhandled exception in SystemUndelete", e);
			log.debug("File System Exception {} tx= {}", () -> e, () -> TextFormat.shortDebugString(op));
			receipt = getTransactionReceipt(FILE_SYSTEM_EXCEPTION, exchange.activeRates());
		}
		TransactionRecord.Builder transactionRecord =
				getTransactionRecord(txBody.getTransactionFee(), txBody.getMemo(),
						txBody.getTransactionID(), getTimestamp(consensusTimestamp), receipt);
		return transactionRecord.build();
	}

	private TransactionReceipt updateDeleteFlag(ContractID cid, boolean deleted) {
		var id = asAccount(cid);
		if (ledger.isDeleted(id)) {
			ledger.customizePotentiallyDeleted(asAccount(cid), new HederaAccountCustomizer().isDeleted(deleted));
		} else {
			ledger.customize(asAccount(cid), new HederaAccountCustomizer().isDeleted(deleted));
		}
		return getTransactionReceipt(SUCCESS, exchange.activeRates());
	}

	/**
	 * Delete an existing contract
	 *
	 * @param transaction
	 * 		API request to delete the contract.
	 * @param consensusTime
	 * 		Platform consensus time
	 * @return Details of contract deletion result
	 */
	public TransactionRecord deleteContract(TransactionBody transaction, Instant consensusTime) {
		TransactionReceipt transactionReceipt;
		ContractDeleteTransactionBody op = transaction.getContractDeleteInstance();

		ContractID cid = op.getContractID();
		ResponseCodeEnum validity = validateContractExistence(cid);
		if (validity == ResponseCodeEnum.OK) {
			AccountID beneficiary = Optional.ofNullable(getBeneficiary(op)).orElse(dynamicProperties.fundingAccount());
			validity = validateContractDelete(op);
			if (validity == SUCCESS) {
				validity = ledger.exists(beneficiary) ? SUCCESS : OBTAINER_DOES_NOT_EXIST;
				if (validity == SUCCESS) {
					validity = ledger.isDeleted(beneficiary)
							? (ledger.isSmartContract(beneficiary) ? CONTRACT_DELETED : ACCOUNT_DELETED)
							: SUCCESS;
				}
			}
			if (validity == SUCCESS) {
				AccountID id = asAccount(cid);
				ledger.delete(id, beneficiary);
			}
			transactionReceipt = getTransactionReceipt(validity, exchange.activeRates());
		} else {
			transactionReceipt = getTransactionReceipt(validity, exchange.activeRates());
		}
		return getTransactionRecord(
				transaction.getTransactionFee(),
				transaction.getMemo(),
				transaction.getTransactionID(),
				getTimestamp(consensusTime),
				transactionReceipt).build();
	}

	private ResponseCodeEnum validateContractDelete(ContractDeleteTransactionBody op) {
		AccountID id = asAccount(op.getContractID());
		if (ledger.getBalance(id) > 0) {
			AccountID beneficiary = getBeneficiary(op);
			if (beneficiary == null) {
				return OBTAINER_REQUIRED;
			} else if (beneficiary.equals(id)) {
				return OBTAINER_SAME_CONTRACT_ID;
			} else if (!ledger.exists(beneficiary) || ledger.isDeleted(beneficiary)) {
				return ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
			}
		}
		return SUCCESS;
	}

	private AccountID getBeneficiary(ContractDeleteTransactionBody op) {
		if (op.hasTransferAccountID()) {
			return ledger.lookUpAliasedId(op.getTransferAccountID(), INVALID_TRANSFER_ACCOUNT_ID).resolvedId();
		} else if (op.hasTransferContractID()) {
			return asAccount(op.getTransferContractID());
		}
		return null;
	}

}
