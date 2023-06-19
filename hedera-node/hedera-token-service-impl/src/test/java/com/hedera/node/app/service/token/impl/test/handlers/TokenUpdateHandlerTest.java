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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateValidator;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private PropertySource compositeProps;

    @Mock(strictness = LENIENT)
    private HederaNumbers hederaNumbers;

    @Mock(strictness = LENIENT)
    private GlobalDynamicProperties dynamicProperties;

    private TransactionBody txn;
    private ExpiryValidator expiryValidator;
    private AttributeValidator attributeValidator;
    private TokenUpdateHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        final TokenUpdateValidator validator = new TokenUpdateValidator(new TokenAttributesValidator());
        subject = new TokenUpdateHandler(validator);
        givenStoresAndConfig(handleContext);
        setUpTxnContext();
    }

    @Test
    void happyPathForFungibleTokenUpdate() {
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(fungibleTokenId);
        assertThat(token.symbol()).isEqualTo(fungibleToken.symbol());
        assertThat(token.name()).isEqualTo(fungibleToken.name());
        assertThat(token.treasuryAccountNumber()).isEqualTo(fungibleToken.treasuryAccountNumber());
        assertThat(token.adminKey()).isEqualTo(fungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(fungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(fungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(fungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(fungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(fungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(fungibleToken.pauseKey());
        assertThat(token.autoRenewAccountNumber()).isEqualTo(fungibleToken.autoRenewAccountNumber());
        assertThat(token.expiry()).isEqualTo(fungibleToken.expiry());
        assertThat(token.memo()).isEqualTo(fungibleToken.memo());
        assertThat(token.autoRenewSecs()).isEqualTo(fungibleToken.autoRenewSecs());
        assertThat(token.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountNumber()).isEqualTo(ownerId.accountNum());
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountNumber()).isEqualTo(ownerId.accountNum());
        assertThat(modifiedToken.expiry()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSecs()).isEqualTo(fungibleToken.autoRenewSecs());
        assertThat(token.tokenType()).isEqualTo(FUNGIBLE_COMMON);
    }

    @Test
    void happyPathForNonFungibleTokenUpdate() {
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(nonFungibleTokenId);
        assertThat(token.symbol()).isEqualTo(nonFungibleToken.symbol());
        assertThat(token.name()).isEqualTo(nonFungibleToken.name());
        assertThat(token.treasuryAccountNumber()).isEqualTo(nonFungibleToken.treasuryAccountNumber());
        assertThat(token.adminKey()).isEqualTo(nonFungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(nonFungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(nonFungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(nonFungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(nonFungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(nonFungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(nonFungibleToken.pauseKey());
        assertThat(token.autoRenewAccountNumber()).isEqualTo(nonFungibleToken.autoRenewAccountNumber());
        assertThat(token.expiry()).isEqualTo(nonFungibleToken.expiry());
        assertThat(token.memo()).isEqualTo(nonFungibleToken.memo());
        assertThat(token.autoRenewSecs()).isEqualTo(nonFungibleToken.autoRenewSecs());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountNumber()).isEqualTo(ownerId.accountNum());
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountNumber()).isEqualTo(ownerId.accountNum());
        assertThat(modifiedToken.expiry()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSecs()).isEqualTo(fungibleToken.autoRenewSecs());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void invalidTokenFails() {
        txn = new TokenUpdateBuilder().withToken(asToken(1000)).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    void failsIfTokenImmutable() {
        final var copyToken = writableTokenStore
                .get(fungibleTokenId)
                .copyBuilder()
                .adminKey((Key) null)
                .build();
        writableTokenStore.put(copyToken);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_IS_IMMUTABLE));
    }

    @Test
    void failsIfTokenHasNoKycGrantedImmutable() {
        final var copyTokenRel = writableTokenRelStore
                .get(treasuryId, fungibleTokenId)
                .copyBuilder()
                .kycGranted(false)
                .build();
        writableTokenRelStore.put(copyTokenRel);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_HAS_NO_KYC_KEY));
    }

    @Test
    void failsIfTokenRelIsFrozen() {
        final var copyTokenRel = writableTokenRelStore
                .get(treasuryId, fungibleTokenId)
                .copyBuilder()
                .frozen(true)
                .build();
        writableTokenRelStore.put(copyTokenRel);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    @Test
    void failsIfMemoTooLong() {
        txn = new TokenUpdateBuilder()
                .withMemo("12345678904634634563436462343254534e5365453435452454524541534353665324545435")
                .build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MEMO_TOO_LONG));
    }

    @Test
    void failsIfMemoHasZeroByte() {
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);
        txn = new TokenUpdateBuilder().withMemo("\0").build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void doesntFailForZeroLengthSymbolUpdate() {
        txn = new TokenUpdateBuilder().withSymbol("").build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void doesntFailForNullSymbol() {
        setUpTxnContext();
        txn = new TokenUpdateBuilder().withSymbol(null).build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void failsForVeryLongSymbol() {
        setUpTxnContext();
        txn = new TokenUpdateBuilder()
                .withSymbol("1234567890123456789012345678901234567890123456789012345678901234567890")
                .build();
        configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.body()).willReturn(txn);

        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        Assertions.assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_SYMBOL_TOO_LONG));
    }

    @Test
    void doesntFailForZeroLengthName() {
        txn = new TokenUpdateBuilder().withName("").build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void doesntFailForNullName() {
        txn = new TokenUpdateBuilder().withName(null).build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void failsForVeryLongName() {
        txn = new TokenUpdateBuilder()
                .withName("1234567890123456789012345678901234567890123456789012345678901234567890")
                .build();
        configuration = new HederaTestConfigBuilder()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.body()).willReturn(txn);

        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        Assertions.assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NAME_TOO_LONG));
    }

    //
    //    @Test
    //    void worksWithUnassociatedNewTreasury() {
    //        final long oldTreasuryBalance = 10;
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(store.associationExists(newTreasury, target)).willReturn(false);
    //        given(store.autoAssociate(newTreasury, target)).willReturn(OK);
    //        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
    //        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
    //        given(ledger.doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance))
    //                .willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(SUCCESS);
    //        verify(ledger).decrementNumTreasuryTitles(oldTreasury);
    //        verify(ledger).incrementNumTreasuryTitles(newTreasury);
    //        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    //    }
    //
    //    @Test
    //    void abortsOnFailedAutoAssociationForUnassociatedNewTreasury() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(store.associationExists(newTreasury, target)).willReturn(false);
    //        given(store.autoAssociate(newTreasury, target)).willReturn(NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnInvalidNewTreasury() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.unfreeze(newTreasury, target)).willReturn(ResponseCodeEnum.INVALID_ACCOUNT_ID);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(ledger).unfreeze(newTreasury, target);
    //        verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_ACCOUNT_ID);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnDetachedNewTreasury() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.usabilityOf(newTreasury)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnDetachedOldTreasury() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.isDetached(oldTreasury)).willReturn(true);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnDetachedOldAutoRenew() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.usabilityOf(newAutoRenew)).willReturn(OK);
    //        given(ledger.isDetached(oldAutoRenew)).willReturn(true);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnDetachedNewAutoRenew() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.usabilityOf(newAutoRenew)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void abortsOnDeletedAutoRenew() {
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.usabilityOf(newAutoRenew)).willReturn(ACCOUNT_DELETED);
    //
    //        subject.doStateTransition();
    //
    //        verify(store, never()).update(any(), anyLong());
    //        verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //    }
    //
    //    @Test
    //    void permitsExtendingExpiry() {
    //        givenValidTxnCtx(false);
    //        given(token.adminKey()).willReturn(Optional.empty());
    //        given(expiryOnlyCheck.test(any())).willReturn(true);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(SUCCESS);
    //    }
    //
    //    @Test
    //    void abortsOnNotSetAdminKey() {
    //        givenValidTxnCtx(true);
    //        given(token.adminKey()).willReturn(Optional.empty());
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(TOKEN_IS_IMMUTABLE);
    //    }
    //
    //    @Test
    //    void abortsOnInvalidNewExpiry() {
    //        final var expiry =
    //                Timestamp.newBuilder().setSeconds(thisSecond + thisSecond).build();
    //
    //        final var builder = TransactionBody.newBuilder()
    //                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder().setExpiry(expiry));
    //        tokenUpdateTxn = builder.build();
    //        given(accessor.getTxn()).willReturn(tokenUpdateTxn);
    //        given(txnCtx.accessor()).willReturn(accessor);
    //        given(validator.isValidExpiry(expiry)).willReturn(false);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
    //    }
    //
    //    @Test
    //    void abortsOnAlreadyDeletedToken() {
    //        givenValidTxnCtx(true);
    //        // and:
    //        given(token.isDeleted()).willReturn(true);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(TOKEN_WAS_DELETED);
    //    }
    //
    //    @Test
    //    void abortsOnPausedToken() {
    //        givenValidTxnCtx(true);
    //        given(token.isPaused()).willReturn(true);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(TOKEN_IS_PAUSED);
    //    }
    //
    //    @Test
    //    void doesntReplaceIdenticalTreasury() {
    //        givenValidTxnCtx(true, true);
    //        givenToken(true, true);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(ledger, never()).getTokenBalance(oldTreasury, target);
    //        verify(ledger, never()).doTokenTransfer(any(), any(), any(), anyLong());
    //        verify(txnCtx).setStatus(SUCCESS);
    //    }
    //
    //    @Test
    //    void followsHappyPathWithNewTreasury() {
    //        // setup:
    //        final long oldTreasuryBalance = 1000;
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
    //        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
    //        given(ledger.doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance))
    //                .willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(txnCtx).setStatus(SUCCESS);
    //        verify(ledger).decrementNumTreasuryTitles(oldTreasury);
    //        verify(ledger).incrementNumTreasuryTitles(newTreasury);
    //        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    //    }
    //
    //    @Test
    //    void followsHappyPathWithNewTreasuryAndZeroBalanceOldTreasury() {
    //        final long oldTreasuryBalance = 0;
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
    //        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
    //
    //        subject.doStateTransition();
    //
    //        verify(ledger).unfreeze(newTreasury, target);
    //        verify(ledger).grantKyc(newTreasury, target);
    //        verify(ledger).getTokenBalance(oldTreasury, target);
    //        verify(ledger, never()).doTokenTransfer(target, oldTreasury, newTreasury, oldTreasuryBalance);
    //        verify(txnCtx).setStatus(SUCCESS);
    //        verify(sigImpactHistorian).markEntityChanged(target.getTokenNum());
    //    }
    //
    //    @Test
    //    void followsHappyPathNftWithNewTreasury() {
    //        final long oldTreasuryBalance = 1;
    //        givenValidTxnCtx(true);
    //        givenToken(true, true);
    //        given(token.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
    //        given(ledger.unfreeze(newTreasury, target)).willReturn(OK);
    //        given(ledger.grantKyc(newTreasury, target)).willReturn(OK);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //        given(ledger.getTokenBalance(oldTreasury, target)).willReturn(oldTreasuryBalance);
    //        given(store.changeOwnerWildCard(nftId, oldTreasury, newTreasury)).willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(ledger).unfreeze(newTreasury, target);
    //        verify(ledger).grantKyc(newTreasury, target);
    //        verify(ledger).getTokenBalance(oldTreasury, target);
    //        verify(store).changeOwnerWildCard(nftId, oldTreasury, newTreasury);
    //        verify(txnCtx).setStatus(SUCCESS);
    //    }
    //
    //    @Test
    //    void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
    //        givenValidTxnCtx(true);
    //        givenToken(false, false);
    //        given(store.update(any(), anyLong())).willReturn(OK);
    //        given(ledger.doTokenTransfer(eq(target), eq(oldTreasury), eq(newTreasury), anyLong()))
    //                .willReturn(OK);
    //
    //        subject.doStateTransition();
    //
    //        verify(ledger, never()).unfreeze(newTreasury, target);
    //        verify(ledger, never()).grantKyc(newTreasury, target);
    //        verify(txnCtx).setStatus(SUCCESS);
    //    }

    @Test
    void validatesUpdatingKeys(){
        txn = new TokenUpdateBuilder().withAdminKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ADMIN_KEY));

        txn = new TokenUpdateBuilder().withSupplyKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_SUPPLY_KEY));

        txn = new TokenUpdateBuilder().withWipeKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_WIPE_KEY));

        txn = new TokenUpdateBuilder().withFeeScheduleKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_SCHEDULE_KEY));

        txn = new TokenUpdateBuilder().withPauseKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_PAUSE_KEY));
    }

        @Test
        void rejectsTreasuryUpdateIfNonzeroBalanceForNFTs() {
            final var copyTokenRel = writableTokenRelStore
                    .get(treasuryId, nonFungibleTokenId)
                    .copyBuilder()
                    .balance(1)
                    .build();
            writableTokenRelStore.put(copyTokenRel);
            given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
            txn = new TokenUpdateBuilder().withToken(nonFungibleTokenId).build();
            given(handleContext.body()).willReturn(txn);
            assertThatThrownBy(() -> subject.handle(handleContext))
                    .isInstanceOf(HandleException.class)
                    .has(responseCode(CURRENT_TREASURY_STILL_OWNS_NFTS));
        }

    /* --------------------------------- Helpers --------------------------------- */
    /**
     * A builder for {@link com.hedera.hapi.node.transaction.TransactionBody} instances.
     */
    private class TokenUpdateBuilder {
        private AccountID payer = payerId;
        private AccountID treasury = ownerId;
        private Key adminKey = B_COMPLEX_KEY;
        private String name = "TestToken1";
        private String symbol = "TTT";
        private Key kycKey = B_COMPLEX_KEY;
        private Key freezeKey = B_COMPLEX_KEY;
        private Key wipeKey = B_COMPLEX_KEY;
        private Key supplyKey = B_COMPLEX_KEY;
        private Key feeScheduleKey = B_COMPLEX_KEY;
        private Key pauseKey = B_COMPLEX_KEY;
        private Timestamp expiry = Timestamp.newBuilder().seconds(1234600L).build();
        private AccountID autoRenewAccount = ownerId;
        private long autoRenewPeriod = autoRenewSecs;
        private String memo = "test token1";
        TokenID tokenId = fungibleTokenId;

        private TokenUpdateBuilder() {}

        public TransactionBody build() {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody = TokenUpdateTransactionBody.newBuilder()
                    .token(tokenId)
                    .symbol(symbol)
                    .name(name)
                    .treasury(treasury)
                    .adminKey(adminKey)
                    .supplyKey(supplyKey)
                    .kycKey(kycKey)
                    .freezeKey(freezeKey)
                    .wipeKey(wipeKey)
                    .feeScheduleKey(feeScheduleKey)
                    .pauseKey(pauseKey)
                    .autoRenewAccount(autoRenewAccount)
                    .expiry(expiry)
                    .memo(memo);
            if (autoRenewPeriod > 0) {
                createTxnBody.autoRenewPeriod(
                        Duration.newBuilder().seconds(autoRenewPeriod).build());
            }
            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .tokenUpdate(createTxnBody.build())
                    .build();
        }

        public TokenUpdateBuilder withToken(TokenID tokenId) {
            this.tokenId = tokenId;
            return this;
        }

        public TokenUpdateBuilder withFreezeKey(Key freezeKey) {
            this.freezeKey = freezeKey;
            return this;
        }

        public TokenUpdateBuilder withAutoRenewAccount(AccountID autoRenewAccount) {
            this.autoRenewAccount = autoRenewAccount;
            return this;
        }

        public TokenUpdateBuilder withSymbol(final String symbol) {
            this.symbol = symbol;
            return this;
        }

        public TokenUpdateBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        public TokenUpdateBuilder withTreasury(final AccountID treasury) {
            this.treasury = treasury;
            return this;
        }

        public TokenUpdateBuilder withFeeScheduleKey(final Key key) {
            this.feeScheduleKey = key;
            return this;
        }

        public TokenUpdateBuilder withAdminKey(final Key key) {
            this.adminKey = key;
            return this;
        }

        public TokenUpdateBuilder withSupplyKey(final Key key) {
            this.supplyKey = key;
            return this;
        }

        public TokenUpdateBuilder withKycKey(final Key key) {
            this.kycKey = key;
            return this;
        }

        public TokenUpdateBuilder withWipeKey(final Key key) {
            this.wipeKey = key;
            return this;
        }

        public TokenUpdateBuilder withExpiry(final long expiry) {
            this.expiry = Timestamp.newBuilder().seconds(expiry).build();
            return this;
        }

        public TokenUpdateBuilder withAutoRenewPeriod(final long autoRenewPeriod) {
            this.autoRenewPeriod = autoRenewPeriod;
            return this;
        }

        public TokenUpdateBuilder withMemo(final String s) {
            this.memo = s;
            return this;
        }

        public TokenUpdateBuilder withPauseKey(final Key key) {
            this.pauseKey = key;
            return this;
        }
    }

    private void setUpTxnContext() {
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(compositeProps.getLongProperty("entities.maxLifetime")).willReturn(7200000L);

        attributeValidator =
                new StandardizedAttributeValidator(consensusInstant::getEpochSecond, compositeProps, dynamicProperties);
        expiryValidator = new StandardizedExpiryValidator(
                id -> {
                    final var account = writableAccountStore.get(
                            AccountID.newBuilder().accountNum(id.num()).build());
                    validateTrue(account != null, INVALID_AUTORENEW_ACCOUNT);
                },
                attributeValidator,
                consensusInstant::getEpochSecond,
                hederaNumbers,
                configProvider);

        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(3000000L);
        given(dynamicProperties.minAutoRenewDuration()).willReturn(10L);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);
    }
}
