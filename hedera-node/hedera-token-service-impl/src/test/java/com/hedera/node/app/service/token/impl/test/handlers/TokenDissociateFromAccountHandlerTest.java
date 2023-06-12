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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ALIASES_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKENS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.mockStates;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.mockWritableStates;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.ParityTestBase;
import com.hedera.node.app.service.token.impl.util.IdConvenienceUtils;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenDissociateFromAccountHandlerTest extends ParityTestBase {
    private static final AccountID ACCOUNT_1339 =
            AccountID.newBuilder().accountNum(MISC_ACCOUNT.getAccountNum()).build();
    private static final AccountID ACCOUNT_2020 = IdConvenienceUtils.fromAccountNum(2020);
    private static final TokenID TOKEN_555_ID =
            TokenID.newBuilder().tokenNum(555).build();
    private static final TokenID TOKEN_666_ID =
            TokenID.newBuilder().tokenNum(666).build();

    private final TokenDissociateFromAccountHandler subject = new TokenDissociateFromAccountHandler();

    @Nested
    class PreHandleTests {
        @SuppressWarnings("DataFlowIssue")
        @Test
        void nullArgsThrows() {
            assertThatThrownBy(() -> subject.preHandle(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void pureChecksRejectsDissociateWithMissingAccount() {
            final var txn = newDissociateTxn(null, List.of(TOKEN_555_ID));

            assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void pureChecksRejectsDissociateWithRepeatedTokenId() {
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID, TOKEN_666_ID, TOKEN_555_ID));

            assertThatThrownBy(() -> subject.pureChecks(txn))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(TOKEN_ID_REPEATED_IN_TOKEN_LIST));
        }

        @Test
        void tokenDissociateWithKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), Matchers.contains(MISC_ACCOUNT_KT.asPbjKey()));
        }

        @Test
        void tokenDissociateWithSelfPaidKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(0, context.requiredNonPayerKeys().size());
        }

        @Test
        void tokenDissociateWithCustomPaidKnownTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            subject.preHandle(context);

            assertEquals(1, context.requiredNonPayerKeys().size());
            assertThat(context.requiredNonPayerKeys(), Matchers.contains(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey()));
        }

        @Test
        void tokenDissociateWithMissingTargetScenario() throws PreCheckException {
            final var theTxn = txnFrom(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

            final var context = new FakePreHandleContext(readableAccountStore, theTxn);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }
    }

    @Nested
    class HandleTests {
        @Test
        void rejectsNonexistingAccount() {
            final var context = mockContext();
            final var txn = newDissociateTxn(AccountID.newBuilder().build(), List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(INVALID_ACCOUNT_ID));
        }

        @Test
        void rejectsExpiredAccount() {
            // Create an account that is expired
            final var accountNumber = 12345L;
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(accountNumber)
                    .expiredAndPendingRemoval(true)
                    .deleted(false)
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(
                    AccountID.newBuilder().accountNum(accountNumber).build(), List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
        }

        @Test
        void rejectsDeletedAccount() {
            // Create an account that is deleted
            final var accountNumber = 53135;
            writableAccountStore.put(Account.newBuilder()
                    .accountNumber(accountNumber)
                    .expiredAndPendingRemoval(false)
                    .deleted(true)
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(
                    AccountID.newBuilder().accountNum(accountNumber).build(), List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_DELETED));
        }

        @Test
        void rejectsNonexistingTokenRel() {
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @Test
        void rejectsPausedToken() {
            // Create a readable store with a paused token
            final var pausedToken = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .paused(true)
                    .build();
            readableTokenStore = newReadableStoreWithTokens(pausedToken);

            // Create the token rel for the paused token
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(TOKEN_IS_PAUSED));
        }

        @Test
        void rejectsTreasuryAccount() {
            // Create a readable store that has a token with a treasury account
            final var tokenWithTreasury = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .treasuryAccountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .build();
            readableTokenStore = newReadableStoreWithTokens(tokenWithTreasury);

            // Create the token rel
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_IS_TREASURY));
        }

        @Test
        void rejectsFrozenToken() {
            // Create the readable store with a token
            final var tokenWithTreasury =
                    Token.newBuilder().tokenNumber(TOKEN_555_ID.tokenNum()).build();
            readableTokenStore = newReadableStoreWithTokens(tokenWithTreasury);

            // Create the frozen token rel
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .frozen(true)
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_FROZEN_FOR_TOKEN));
        }

        @Test
        void rejectsAccountThatStillOwnsNfts() {
            // Create the readable store with a token that still owns an NFT
            final var tokenWithTreasury = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .build();
            readableTokenStore = newReadableStoreWithTokens(tokenWithTreasury);

            // Create the token rel with a non-zero NFT balance
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .balance(1L)
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            Assertions.assertThatThrownBy(() -> subject.handle(context))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(ACCOUNT_STILL_OWNS_NFTS));
        }

        @Test
        void rejectsAccountThatStillOwnsUnexpiredFungibleUnits() {
            // @future('6864'): implement when token expiry is implemented
        }

        @Test
        void tokenRelForDeletedTokenIsRemoved() {
            // Create the writable account store with an account
            final var accountWithTokenRels = Account.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .headTokenNumber(TOKEN_555_ID.tokenNum())
                    .numberAssociations(1)
                    .usedAutoAssociations(0)
                    .build();
            writableAccountStore = newWritableStoreWithAccounts(accountWithTokenRels);

            // Create the readable token store with a deleted token
            final var tokenWithTreasury = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .deleted(true)
                    .build();
            readableTokenStore = newReadableStoreWithTokens(tokenWithTreasury);

            // Create the token rel for the deleted token
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            subject.handle(context);

            // Verify the account is in its expected state
            final var savedAcct = writableAccountStore.get(ACCOUNT_1339);
            Assertions.assertThat(savedAcct).isNotNull();
            // Since only one token was associated with the account before handle(), the number of
            // associations should now be zero and the head token number should not exist (i.e. be -1)
            Assertions.assertThat(savedAcct.numberAssociations()).isZero();
            Assertions.assertThat(savedAcct.headTokenNumber()).isEqualTo(-1);
            // Since the account had no auto-associated tokens, usedAutoAssociations should still be zero
            Assertions.assertThat(savedAcct.usedAutoAssociations()).isZero();
            // Since the account had no positive balances, its number of positive balances should still be zero
            Assertions.assertThat(savedAcct.numberPositiveBalances()).isZero();
            // Verify that the token rel was removed
            final var supposedlyDeletedTokenRel = writableTokenRelStore.get(ACCOUNT_1339, TOKEN_555_ID);
            Assertions.assertThat(supposedlyDeletedTokenRel).isNull();
        }

        @Test
        void tokenRelForNonexistingTokenIsRemoved() {
            // Create the writable account store with an account
            final var accountWithTokenRels = Account.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .headTokenNumber(TOKEN_555_ID.tokenNum())
                    .numberAssociations(1)
                    .usedAutoAssociations(0)
                    .build();
            writableAccountStore = newWritableStoreWithAccounts(accountWithTokenRels);

            // Verify that the token doesn't exist
            final var token = readableTokenStore.get(TOKEN_555_ID);
            Assertions.assertThat(token).isNull();

            // Create the token rel for the nonexistent token
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            subject.handle(context);

            // Verify the account is in its expected state
            final var savedAcct = writableAccountStore.get(ACCOUNT_1339);
            Assertions.assertThat(savedAcct).isNotNull();
            // Since only one token was associated with the account before handle(), the number of
            // associations should now be zero and the head token number should not exist (i.e. be -1)
            Assertions.assertThat(savedAcct.numberAssociations()).isZero();
            Assertions.assertThat(savedAcct.headTokenNumber()).isEqualTo(-1);
            // Since the account had no auto-associated tokens, auto associations should still be zero
            Assertions.assertThat(savedAcct.usedAutoAssociations()).isZero();
            // Since the account had no positive balances, its number of positive balances should still be zero
            Assertions.assertThat(savedAcct.numberPositiveBalances()).isZero();
            // Verify that the token rel was removed
            final var supposedlyDeletedTokenRel = writableTokenRelStore.get(ACCOUNT_1339, TOKEN_555_ID);
            Assertions.assertThat(supposedlyDeletedTokenRel).isNull();
        }

        @Test
        void tokenRelAndTreasuryTokenRelAreUpdatedForFungible() {
            // i.e. verify the token rel is removed and the treasury token rel balance is updated

            // Create the writable account store with account 1339 and the treasury account
            final var accountWithTokenRels = Account.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .headTokenNumber(TOKEN_555_ID.tokenNum())
                    .numberAssociations(1)
                    .usedAutoAssociations(0)
                    .numberPositiveBalances(1)
                    .build();
            final var treasuryAccount = Account.newBuilder()
                    .accountNumber(ACCOUNT_2020.accountNumOrThrow())
                    .headTokenNumber(TOKEN_555_ID.tokenNum())
                    .numberAssociations(1)
                    .usedAutoAssociations(1)
                    .build();
            writableAccountStore = newWritableStoreWithAccounts(accountWithTokenRels, treasuryAccount);

            // Create the readable store with a token that:
            // 1. still has a fungible token balance
            // 2. has a treasury account
            final var totalSupply = 3000L;
            final var tokenWithTreasury = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .treasuryAccountNumber(ACCOUNT_2020.accountNumOrThrow())
                    .totalSupply(totalSupply)
                    .build();
            readableTokenStore = newReadableStoreWithTokens(tokenWithTreasury);

            // Create the token rel with a non-zero fungible balance
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .balance(1000)
                    .build());
            // Create the treasury token rel
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_2020.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .balance(2000L)
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(TOKEN_555_ID));
            given(context.body()).willReturn(txn);

            subject.handle(context);

            // Verify the account is in its expected state
            final var savedAcct = writableAccountStore.get(ACCOUNT_1339);
            Assertions.assertThat(savedAcct).isNotNull();
            // Since only one token was associated with the account before handle(), the number of
            // associations should now be zero and the head token number should not exist (i.e. be -1)
            Assertions.assertThat(savedAcct.numberAssociations()).isZero();
            Assertions.assertThat(savedAcct.headTokenNumber()).isEqualTo(-1);
            // Since the account had no auto-associated tokens, auto associations should still be zero
            Assertions.assertThat(savedAcct.usedAutoAssociations()).isZero();
            // Since the removed token rel had a positive balance, the account's number of positive balances should also
            // be reduced
            Assertions.assertThat(savedAcct.numberPositiveBalances()).isZero();

            // Verify the treasury account is in its expected state
            final var treasuryAcct = writableAccountStore.get(ACCOUNT_2020);
            Assertions.assertThat(treasuryAcct).isNotNull();
            // The treasury should still be associated with the token
            Assertions.assertThat(treasuryAcct.numberAssociations()).isEqualTo(1);
            Assertions.assertThat(treasuryAcct.headTokenNumber()).isEqualTo(TOKEN_555_ID.tokenNum());

            // Verify that the token rel with account 1339 was removed
            final var supposedlyDeletedTokenRel = writableTokenRelStore.get(ACCOUNT_1339, TOKEN_555_ID);
            Assertions.assertThat(supposedlyDeletedTokenRel).isNull();

            // Verify that the token rel with the treasury account was updated
            final var treasuryTokenRel = writableTokenRelStore.get(ACCOUNT_2020, TOKEN_555_ID);
            Assertions.assertThat(treasuryTokenRel).isNotNull();
            // Verify that the treasury balance is now equal to its supply
            Assertions.assertThat(treasuryTokenRel.balance()).isEqualTo(totalSupply);
        }

        @Test
        void multipleTokenRelsAreRemoved() {
            // Represents a token that won't be found
            final var token444Id = IdConvenienceUtils.fromTokenNum(444);
            // Represents a token that is deleted
            final var token555 = Token.newBuilder()
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .deleted(true)
                    .build();
            // Represents an active token
            final var token666 = Token.newBuilder()
                    .tokenNumber(TOKEN_666_ID.tokenNum())
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .build();

            // Create the writable account store with an account
            final var accountWithTokenRels = Account.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .headTokenNumber(token444Id.tokenNum())
                    .numberAssociations(3)
                    .usedAutoAssociations(1) // We'll set up the active token rel as auto-associated
                    .numberPositiveBalances(2)
                    .build();
            writableAccountStore = newWritableStoreWithAccounts(accountWithTokenRels);

            // Create the readable token store with a deleted token
            readableTokenStore = newReadableStoreWithTokens(
                    token555, token666); // 444 not added intentionally to simulate it not being in the store

            // Create the token rel for each token
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(token444Id.tokenNum())
                    .previousToken(-1) // start of the account's token list
                    .nextToken(TOKEN_555_ID.tokenNum())
                    .balance(20)
                    .build());
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_555_ID.tokenNum())
                    .previousToken(token444Id.tokenNum())
                    .nextToken(TOKEN_666_ID.tokenNum())
                    .balance(30)
                    .automaticAssociation(true)
                    .build());
            writableTokenRelStore.put(TokenRelation.newBuilder()
                    .accountNumber(ACCOUNT_1339.accountNumOrThrow())
                    .tokenNumber(TOKEN_666_ID.tokenNum())
                    .previousToken(TOKEN_555_ID.tokenNum())
                    .nextToken(-1) // end of the account's token list
                    .build());

            // Create the context and transaction
            final var context = mockContext();
            final var txn = newDissociateTxn(ACCOUNT_1339, List.of(token444Id, TOKEN_555_ID, TOKEN_666_ID));
            given(context.body()).willReturn(txn);

            subject.handle(context);

            // Verify the account is in its expected state
            final var savedAcct = writableAccountStore.get(ACCOUNT_1339);
            Assertions.assertThat(savedAcct).isNotNull();
            // After deleting all three token relations, the number of associations should now be
            // zero and the head token number should not exist (i.e. be -1)
            Assertions.assertThat(savedAcct.numberAssociations()).isZero();
            Assertions.assertThat(savedAcct.headTokenNumber()).isEqualTo(-1);
            // The active token, which is the only auto-associated token rel, should have been removed
            Assertions.assertThat(savedAcct.usedAutoAssociations()).isZero();
            // Both token rels with positive balances should have been removed, so the account's number of positive
            // balances should now be zero
            Assertions.assertThat(savedAcct.numberPositiveBalances()).isZero();

            // Verify that the token rels were removed
            final var token444Rel = writableTokenRelStore.get(ACCOUNT_1339, token444Id);
            Assertions.assertThat(token444Rel).isNull();
            final var token555Rel = writableTokenRelStore.get(ACCOUNT_1339, TOKEN_555_ID);
            Assertions.assertThat(token555Rel).isNull();
            final var token666Rel = writableTokenRelStore.get(ACCOUNT_1339, TOKEN_666_ID);
            Assertions.assertThat(token666Rel).isNull();
        }

        private ReadableTokenStore newReadableStoreWithTokens(Token... tokens) {
            final var backingMap = new HashMap<EntityNum, Token>();
            for (final Token token : tokens) {
                backingMap.put(
                        EntityNum.fromTokenId(fromPbj(IdConvenienceUtils.fromTokenNum(token.tokenNumber()))), token);
            }

            final var wrappingState = new MapWritableKVState<>(TOKENS_KEY, backingMap);
            return new ReadableTokenStoreImpl(mockStates(Map.of(TOKENS_KEY, wrappingState)));
        }

        private WritableAccountStore newWritableStoreWithAccounts(Account... accounts) {
            final var backingMap = new HashMap<AccountID, Account>();
            for (final Account account : accounts) {
                backingMap.put(IdConvenienceUtils.fromAccountNum(account.accountNumber()), account);
            }

            final var wrappingState = new MapWritableKVState<>(ACCOUNTS_KEY, backingMap);
            return new WritableAccountStore(mockWritableStates(Map.of(
                    ACCOUNTS_KEY, wrappingState, ALIASES_KEY, new MapWritableKVState<>(ALIASES_KEY, new HashMap<>()))));
        }
    }

    private HandleContext mockContext() {
        final var handleContext = mock(HandleContext.class);

        final var expiryValidator = mock(ExpiryValidator.class);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.OK);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochMilli(0L));

        lenient().when(handleContext.writableStore(WritableAccountStore.class)).thenReturn(writableAccountStore);
        lenient().when(handleContext.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        lenient()
                .when(handleContext.writableStore(WritableTokenRelationStore.class))
                .thenReturn(writableTokenRelStore);

        return handleContext;
    }

    private TransactionBody newDissociateTxn(AccountID account, List<TokenID> tokens) {
        TokenDissociateTransactionBody.Builder associateTxnBodyBuilder = TokenDissociateTransactionBody.newBuilder();
        if (account != null) associateTxnBodyBuilder.account(account);
        if (tokens != null) associateTxnBodyBuilder.tokens(tokens);
        return TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_1339).build())
                .tokenDissociate(associateTxnBodyBuilder)
                .build();
    }
}
