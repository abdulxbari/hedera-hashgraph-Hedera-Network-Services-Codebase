/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.transferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.wrapIntoTupleArray;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AtomicCryptoTransferHTSSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(AtomicCryptoTransferHTSSuite.class);

    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[] {};
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String FUNGIBLE_TOKEN = "TokenA";
    private static final String NFT_TOKEN = "Token_NFT";
    private static final String TOKEN_TREASURY = "treasury";
    private static final String RECEIVER = "receiver";
    private static final String RECEIVER2 = "receiver2";
    private static final String SENDER = "sender";
    private static final String SENDER2 = "sender2";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);

    private static final String DELEGATE_KEY = "contractKey";
    private static final String CONTRACT = "AtomicCryptoTransfer";
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "Owner";

    public static void main(String... args) {
        new AtomicCryptoTransferHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    cryptoTransferForHbarOnly(),
                    cryptoTransferForFungibleTokenOnly(),
                    cryptoTransferForNonFungibleTokenOnly(),
                    cryptoTransferHBarFungibleNft(),
                    cryptoTransferAllowanceHbarToken(),
                    cryptoTransferAllowanceFungibleToken(),
                    cryptoTransferAllowanceNft()
                });
    }

    private HapiApiSpec cryptoTransferForHbarOnly() {
        final var cryptoTransferTxn = "cryptoTransferTxn";
        final var cryptoTransferMultiTxn = "cryptoTransferMultiTxn";
        final var cryptoTransferRevertTxn = "cryptoTransferRevertTxn";
        final var cryptoTransferRevertNoKeyTxn = "cryptoTransferRevertNoKeyTxn";
        final var cryptoTransferRevertBalanceTooLowTxn = "cryptoTransferRevertBalanceTooLowTxn";

        return defaultHapiSpec("cryptoTransferForHbarOnly")
                .given(
                        overriding("contracts.allowAutoAssociations", "true"),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER2).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER)
                                .balance(2 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true),
                        cryptoCreate(RECEIVER2)
                                .balance(2 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var sender = spec.registry().getAccountID(SENDER);
                                    final var sender2 = spec.registry().getAccountID(SENDER2);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);
                                    final var receiver2 = spec.registry().getAccountID(RECEIVER2);
                                    final var amountToBeSent = 50 * ONE_HBAR;

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                            // transfer between 2 parties
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender,
                                                                                    -amountToBeSent,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    amountToBeSent,
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferTxn)
                                                    .gas(GAS_TO_OFFER),
                                            // Sender does not have key
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender2,
                                                                                    -amountToBeSent,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    amountToBeSent,
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferRevertNoKeyTxn)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // Sender balance is too low
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender,
                                                                                    -1000
                                                                                            * ONE_HUNDRED_HBARS,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    1000
                                                                                            * ONE_HUNDRED_HBARS,
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferRevertBalanceTooLowTxn)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // transfer among 3 parties
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender,
                                                                                    -amountToBeSent,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    amountToBeSent
                                                                                            - (10
                                                                                                    * ONE_HBAR),
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver2,
                                                                                    amountToBeSent
                                                                                            - (40
                                                                                                    * ONE_HBAR),
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferMultiTxn)
                                                    .gas(GAS_TO_OFFER),
                                            // transfer does not add up to 0
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    sender,
                                                                                    -amountToBeSent,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    amountToBeSent
                                                                                            - (5
                                                                                                    * ONE_HBAR),
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver2,
                                                                                    amountToBeSent
                                                                                            - (40
                                                                                                    * ONE_HBAR),
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferRevertTxn)
                                                    .gas(GAS_TO_OFFER)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
                                }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getAccountBalance(SENDER).hasTinyBars(900 * ONE_HBAR),
                        getAccountBalance(RECEIVER).hasTinyBars(290 * ONE_HBAR),
                        getAccountBalance(RECEIVER2).hasTinyBars(210 * ONE_HBAR),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .transfers(
                                                including(
                                                        tinyBarsFromTo(
                                                                SENDER, RECEIVER, 50 * ONE_HBAR)))),
                        childRecordsCheck(
                                cryptoTransferRevertNoKeyTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                cryptoTransferRevertBalanceTooLowTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INSUFFICIENT_ACCOUNT_BALANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INSUFFICIENT_ACCOUNT_BALANCE)))),
                        childRecordsCheck(
                                cryptoTransferMultiTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))),
                        childRecordsCheck(
                                cryptoTransferRevertTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_ACCOUNT_AMOUNTS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_ACCOUNT_AMOUNTS)))));
    }

    private HapiApiSpec cryptoTransferForFungibleTokenOnly() {
        final var cryptoTransferTxn = "cryptoTransferTxn";

        return defaultHapiSpec("cryptoTransferForFungibleTokenOnly")
                .given(
                        overriding("contracts.allowAutoAssociations", "true"),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER)
                                .balance(2 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
                                .logged())
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                                    final var sender = spec.registry().getAccountID(SENDER);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);
                                    final var amountToBeSent = 50L;

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            sender,
                                                                                            -amountToBeSent,
                                                                                            false),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            amountToBeSent,
                                                                                            false))
                                                                            .build()))
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferTxn)
                                                    .gas(GAS_TO_OFFER));
                                }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 50),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 150),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(FUNGIBLE_TOKEN, SENDER, -50)
                                                        .including(FUNGIBLE_TOKEN, RECEIVER, 50))));
    }

    private HapiApiSpec cryptoTransferForNonFungibleTokenOnly() {
        final var cryptoTransferTxn = "cryptoTransferTxn";

        return defaultHapiSpec("cryptoTransferForNonFungibleTokenOnly")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(NFT_TOKEN)),
                        mintToken(
                                NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
                        cryptoTransfer(
                                        TokenMovement.movingUnique(NFT_TOKEN, 1)
                                                .between(TOKEN_TREASURY, SENDER))
                                .payingWith(SENDER),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var token = spec.registry().getTokenID(NFT_TOKEN);
                                    final var sender = spec.registry().getAccountID(SENDER);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withNftTransfers(
                                                                                    nftTransfer(
                                                                                            sender,
                                                                                            receiver,
                                                                                            1L,
                                                                                            false))
                                                                            .build()))
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferTxn)
                                                    .gas(GAS_TO_OFFER));
                                }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER).hasOwnedNfts(0),
                        getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                NonFungibleTransfers.changingNFTBalances()
                                                        .including(
                                                                NFT_TOKEN, SENDER, RECEIVER, 1L))));
    }

    private HapiApiSpec cryptoTransferHBarFungibleNft() {
        final var cryptoTransferTxn = "cryptoTransferTxn";

        return defaultHapiSpec("cryptoTransferHBarFungibleNft")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER)
                                .balance(2 * ONE_HUNDRED_HBARS)
                                .receiverSigRequired(true),
                        cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER2).receiverSigRequired(true),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
                        mintToken(
                                NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
                        tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
                        tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
                        cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER))
                                .payingWith(GENESIS),
                        cryptoTransfer(
                                        TokenMovement.movingUnique(NFT_TOKEN, 1)
                                                .between(TOKEN_TREASURY, SENDER2))
                                .payingWith(SENDER2),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var fungibleToken =
                                            spec.registry().getTokenID(FUNGIBLE_TOKEN);
                                    final var nonFungibleToken =
                                            spec.registry().getTokenID(NFT_TOKEN);
                                    final var fungibleTokenSender =
                                            spec.registry().getAccountID(SENDER);
                                    final var fungibleTokenReceiver =
                                            spec.registry().getAccountID(RECEIVER);
                                    final var nonFungibleTokenSender =
                                            spec.registry().getAccountID(SENDER2);
                                    final var nonFungibleTokenReceiver =
                                            spec.registry().getAccountID(RECEIVER2);
                                    final var amountToBeSent = 50 * ONE_HBAR;

                                    allRunFor(
                                            spec,
                                            newKeyNamed(DELEGATE_KEY)
                                                    .shape(
                                                            DELEGATE_CONTRACT_KEY_SHAPE.signedWith(
                                                                    sigs(ON, CONTRACT))),
                                            cryptoUpdate(SENDER).key(DELEGATE_KEY),
                                            cryptoUpdate(SENDER2).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
                                            cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
                                            contractCall(
                                                            CONTRACT,
                                                            "transferMultipleTokens",
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    fungibleTokenSender,
                                                                                    -amountToBeSent,
                                                                                    false),
                                                                            accountAmount(
                                                                                    fungibleTokenReceiver,
                                                                                    amountToBeSent,
                                                                                    false))
                                                                    .build(),
                                                            tokenTransferLists()
                                                                    .withTokenTransferList(
                                                                            tokenTransferList()
                                                                                    .forToken(
                                                                                            fungibleToken)
                                                                                    .withAccountAmounts(
                                                                                            accountAmount(
                                                                                                    fungibleTokenSender,
                                                                                                    -45L,
                                                                                                    false),
                                                                                            accountAmount(
                                                                                                    fungibleTokenReceiver,
                                                                                                    45L,
                                                                                                    false))
                                                                                    .build(),
                                                                            tokenTransferList()
                                                                                    .forToken(
                                                                                            nonFungibleToken)
                                                                                    .withNftTransfers(
                                                                                            nftTransfer(
                                                                                                    nonFungibleTokenSender,
                                                                                                    nonFungibleTokenReceiver,
                                                                                                    1L,
                                                                                                    false))
                                                                                    .build())
                                                                    .build())
                                                    .payingWith(GENESIS)
                                                    .via(cryptoTransferTxn)
                                                    .gas(GAS_TO_OFFER));
                                }),
                        getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged())
                .then(
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 45),
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 155),
                        getTokenInfo(FUNGIBLE_TOKEN).logged(),
                        getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
                        getAccountInfo(RECEIVER2).hasOwnedNfts(1),
                        getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
                        getAccountInfo(SENDER2).hasOwnedNfts(0),
                        getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
                        getTokenInfo(NFT_TOKEN).logged(),
                        getAccountBalance(SENDER).hasTinyBars(950 * ONE_HBAR),
                        getAccountBalance(RECEIVER).hasTinyBars(250 * ONE_HBAR),
                        childRecordsCheck(
                                cryptoTransferTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(FUNGIBLE_TOKEN, SENDER, -45L)
                                                        .including(FUNGIBLE_TOKEN, RECEIVER, 45L))
                                        .tokenTransfers(
                                                NonFungibleTransfers.changingNFTBalances()
                                                        .including(
                                                                NFT_TOKEN, SENDER2, RECEIVER2, 1L))
                                        .transfers(
                                                including(
                                                        tinyBarsFromTo(
                                                                SENDER,
                                                                RECEIVER,
                                                                50 * ONE_HBAR)))));
    }

    private HapiApiSpec cryptoTransferAllowanceHbarToken() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        final var successfulTransferFromTxn2 = "txn2";
        final var revertingTransferFromTxn = "revertWhenMoreThanAllowance";
        final var revertingTransferFromTxn2 = "revertingTxn";
        final var revertingTransferFromTxn3 = "revertingTxnNoApproval";
        final var transferMultipleTokens = "transferMultipleTokens";
        return defaultHapiSpec("cryptoTransferAllowanceHbarToken")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(OWNER, CONTRACT, allowance)
                                .via("baseApproveTxn")
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .logged(),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(accountWith().cryptoAllowancesContaining(CONTRACT, allowance))
                                .logged(),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var owner = spec.registry().getAccountID(OWNER);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);

                                    allRunFor(
                                            spec,
                                            // trying to transfer more than allowance should
                                            // revert
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    owner,
                                                                                    -(allowance
                                                                                            + 1),
                                                                                    true),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    allowance + 1,
                                                                                    true))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .via(revertingTransferFromTxn)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // revert due to no approval
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    owner,
                                                                                    -allowance,
                                                                                    false),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    allowance,
                                                                                    false))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .via(revertingTransferFromTxn3)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // transfer allowance/2 amount
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    owner,
                                                                                    -allowance / 2,
                                                                                    true),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    allowance / 2,
                                                                                    true))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .via(successfulTransferFromTxn)
                                                    .hasKnownStatus(SUCCESS),
                                            // transfer the rest of the allowance
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    owner,
                                                                                    -allowance / 2,
                                                                                    true),
                                                                            accountAmount(
                                                                                    receiver,
                                                                                    allowance / 2,
                                                                                    true))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .via(successfulTransferFromTxn2)
                                                    .hasKnownStatus(SUCCESS),
                                            getAccountDetails(OWNER)
                                                    .payingWith(GENESIS)
                                                    .has(accountWith().noAllowances()),
                                            // no allowance left, should fail
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            accountAmount(
                                                                                    owner, -1L,
                                                                                    true),
                                                                            accountAmount(
                                                                                    receiver, 1L,
                                                                                    true))
                                                                    .build(),
                                                            EMPTY_TUPLE_ARRAY)
                                                    .via(revertingTransferFromTxn2)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
                                }))
                .then(
                        childRecordsCheck(
                                revertingTransferFromTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(AMOUNT_EXCEEDS_ALLOWANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                AMOUNT_EXCEEDS_ALLOWANCE)))),
                        childRecordsCheck(
                                revertingTransferFromTxn3,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulTransferFromTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .transfers(
                                                including(
                                                        tinyBarsFromTo(
                                                                OWNER, RECEIVER, allowance / 2)))),
                        childRecordsCheck(
                                successfulTransferFromTxn2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .transfers(
                                                including(
                                                        tinyBarsFromTo(
                                                                OWNER, RECEIVER, allowance / 2)))),
                        childRecordsCheck(
                                revertingTransferFromTxn2,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                SPENDER_DOES_NOT_HAVE_ALLOWANCE)))));
    }

    private HapiApiSpec cryptoTransferAllowanceFungibleToken() {
        final var allowance = 10L;
        final var successfulTransferFromTxn = "txn";
        final var successfulTransferFromTxn2 = "txn2";
        final var revertingTransferFromTxn = "revertWhenMoreThanAllowance";
        final var revertingTransferFromTxn2 = "revertingTxn";
        final var revertingTransferFromTxn3 = "revertingTxnNoApproval";
        final var transferMultipleTokens = "transferMultipleTokens";
        return defaultHapiSpec("CryptoTransferAllowanceFungibleToken")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(10L)
                                .maxSupply(1000L)
                                .supplyKey(MULTI_KEY)
                                .treasury(OWNER),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, CONTRACT, allowance)
                                .via("baseApproveTxn")
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR),
                        getAccountDetails(OWNER)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .tokenAllowancesContaining(
                                                        FUNGIBLE_TOKEN, CONTRACT, allowance)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
                                    final var owner = spec.registry().getAccountID(OWNER);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);

                                    allRunFor(
                                            spec,
                                            // trying to transfer more than allowance should
                                            // revert
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            owner,
                                                                                            -(allowance
                                                                                                    + 1),
                                                                                            true),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            allowance
                                                                                                    + 1,
                                                                                            true))
                                                                            .build()))
                                                    .via(revertingTransferFromTxn)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // revert due to no approval
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            owner,
                                                                                            -allowance,
                                                                                            false),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            allowance,
                                                                                            false))
                                                                            .build()))
                                                    .via(revertingTransferFromTxn3)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // transfer allowance/2 amount
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            owner,
                                                                                            -allowance
                                                                                                    / 2,
                                                                                            true),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            allowance
                                                                                                    / 2,
                                                                                            true))
                                                                            .build()))
                                                    .via(successfulTransferFromTxn)
                                                    .hasKnownStatus(SUCCESS),
                                            // transfer the rest of the allowance
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            owner,
                                                                                            -(allowance
                                                                                                    / 2),
                                                                                            true),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            allowance
                                                                                                    / 2,
                                                                                            true))
                                                                            .build()))
                                                    .via(successfulTransferFromTxn2)
                                                    .hasKnownStatus(SUCCESS),
                                            getAccountDetails(OWNER)
                                                    .payingWith(GENESIS)
                                                    .has(accountWith().noAllowances()),
                                            // no allowance left, should fail
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withAccountAmounts(
                                                                                    accountAmount(
                                                                                            owner,
                                                                                            -1L,
                                                                                            true),
                                                                                    accountAmount(
                                                                                            receiver,
                                                                                            1L,
                                                                                            true))
                                                                            .build()))
                                                    .via(revertingTransferFromTxn2)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED));
                                }))
                .then(
                        childRecordsCheck(
                                revertingTransferFromTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(AMOUNT_EXCEEDS_ALLOWANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                AMOUNT_EXCEEDS_ALLOWANCE)))),
                        childRecordsCheck(
                                revertingTransferFromTxn3,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)))),
                        childRecordsCheck(
                                successfulTransferFromTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                OWNER,
                                                                -allowance / 2)
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                RECEIVER,
                                                                allowance / 2))),
                        childRecordsCheck(
                                successfulTransferFromTxn2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                OWNER,
                                                                -allowance / 2)
                                                        .including(
                                                                FUNGIBLE_TOKEN,
                                                                RECEIVER,
                                                                allowance / 2))),
                        childRecordsCheck(
                                revertingTransferFromTxn2,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                SPENDER_DOES_NOT_HAVE_ALLOWANCE)))));
    }

    private HapiApiSpec cryptoTransferAllowanceNft() {
        final var successfulTransferFromTxn = "txn";
        final var revertingTransferFromTxn = "revertWhenMoreThanAllowance";
        final var transferMultipleTokens = "transferMultipleTokens";
        return defaultHapiSpec("cryptoTransferAllowanceNft")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(5),
                        cryptoCreate(RECEIVER).maxAutomaticTokenAssociations(5),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(OWNER)
                                .initialSupply(0L)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        mintToken(
                                NFT_TOKEN,
                                List.of(
                                        ByteStringUtils.wrapUnsafely("meta1".getBytes()),
                                        ByteStringUtils.wrapUnsafely("meta2".getBytes()))),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(OWNER, NFT_TOKEN, CONTRACT, false, List.of(2L))
                                .via("baseApproveTxn")
                                .signedBy(DEFAULT_PAYER, OWNER)
                                .fee(ONE_HBAR))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var token = spec.registry().getTokenID(NFT_TOKEN);
                                    final var owner = spec.registry().getAccountID(OWNER);
                                    final var receiver = spec.registry().getAccountID(RECEIVER);

                                    allRunFor(
                                            spec,
                                            // trying to transfer NFT that is not approved
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withNftTransfers(
                                                                                    nftTransfer(
                                                                                            owner,
                                                                                            receiver,
                                                                                            1L,
                                                                                            true))
                                                                            .build()))
                                                    .via(revertingTransferFromTxn)
                                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                            // transfer allowed NFT
                                            contractCall(
                                                            CONTRACT,
                                                            transferMultipleTokens,
                                                            transferList()
                                                                    .withAccountAmounts(
                                                                            EMPTY_TUPLE_ARRAY)
                                                                    .build(),
                                                            wrapIntoTupleArray(
                                                                    tokenTransferList()
                                                                            .forToken(token)
                                                                            .withNftTransfers(
                                                                                    nftTransfer(
                                                                                            owner,
                                                                                            receiver,
                                                                                            2L,
                                                                                            true))
                                                                            .build()))
                                                    .via(successfulTransferFromTxn)
                                                    .hasKnownStatus(SUCCESS));
                                }))
                .then(
                        childRecordsCheck(
                                revertingTransferFromTxn,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith()
                                        .status(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(
                                                                                SPENDER_DOES_NOT_HAVE_ALLOWANCE)))),
                        childRecordsCheck(
                                successfulTransferFromTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .withStatus(SUCCESS)))
                                        .tokenTransfers(
                                                NonFungibleTransfers.changingNFTBalances()
                                                        .including(
                                                                NFT_TOKEN, OWNER, RECEIVER, 2L))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
