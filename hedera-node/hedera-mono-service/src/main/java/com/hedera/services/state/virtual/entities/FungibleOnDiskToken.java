/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual.entities;

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.HederaToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

/**
 * todo
 */
public class FungibleOnDiskToken extends PartialMerkleLeaf
    implements Keyed<EntityNum>, MerkleLeaf, VirtualValue, HederaToken {

  static final int CURRENT_VERSION = 1;
  private static final long CLASS_ID = 0x0000L;       //???

  private TokenSupplyType supplyType;
  private int decimals;
  private long lastUsedSerialNumber;
  private long expiry;
  private long maxSupply;
  private long totalSupply;
  private long autoRenewPeriod = UNUSED_AUTO_RENEW_PERIOD;
  private JKey adminKey = UNUSED_KEY;
  private JKey kycKey = UNUSED_KEY;
  private JKey wipeKey = UNUSED_KEY;
  private JKey supplyKey = UNUSED_KEY;
  private JKey freezeKey = UNUSED_KEY;
  private JKey feeScheduleKey = UNUSED_KEY;
  private JKey pauseKey = UNUSED_KEY;
  private String symbol;
  private String name;
  private String memo = DEFAULT_MEMO;
  private boolean deleted;
  private boolean accountsFrozenByDefault;
  private boolean accountsKycGrantedByDefault;
  private boolean paused;
  private EntityId treasury;
  private EntityId autoRenewAccount = null;
  private List<FcCustomFee> feeSchedule = Collections.emptyList();
  private int number;
  private boolean immutable;

  public FungibleOnDiskToken() {
    // Runtime constructor
  }

  public FungibleOnDiskToken(final FungibleOnDiskToken other) {
    throw new NotImplementedException();
  }

  public static FungibleOnDiskToken from(MerkleToken merkleToken) {
    throw new NotImplementedException();
  }

  @Override
  public FungibleOnDiskToken copy() {
    this.immutable = true;
    return new FungibleOnDiskToken(this);
  }

  @Override
  public VirtualValue asReadOnly() {
    return copy();
  }

  @Override
  public void serialize(ByteBuffer byteBuffer) throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public void deserialize(final SerializableDataInputStream inputStream, final int version)
      throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public void serialize(final SerializableDataOutputStream output) throws IOException {
      throw new NotImplementedException();
  }

  @Override
  public long getClassId() {
    return CLASS_ID;
  }

  @Override
  public int getVersion() {
    return CURRENT_VERSION;
  }

  @Override
  public int hashCode() {
    throw new NotImplementedException();
  }

  @Override
  public String toString() {
      if(true)throw new NotImplementedException();
    return MoreObjects.toStringHelper(FungibleOnDiskToken.class)
        //.add("abc", abc)
        .toString();
  }

  @Override
  public boolean equals(final Object obj) {
    throw new NotImplementedException();
  }

  @Override
  public long totalSupply() {
    return totalSupply;
  }

  @Override
  public int decimals() {
    return decimals;
  }

  @Override
  public JKey freezeKeyUnsafe() {
    return null;  //??? what's the diff between freezeKey and freezeKeyUnsafe?
  }

  @Override
  public JKey pauseKey() {
    return pauseKey;
  }

  @Override
  public void setPauseKey(JKey pauseKey) {
    throwIfImmutable("[pauseKey] not settable for immutable instances");
    this.pauseKey = pauseKey;
  }

  @Override
  public void setFreezeKey(JKey freezeKey) {
    throwIfImmutable("[freezeKey] not settable for immutable instances");
    this.freezeKey = freezeKey;
  }

  @Override
  public void setKycKey(JKey kycKey) {
    throwIfImmutable("[kycKey] not settable for immutable instances");
    this.kycKey = kycKey;
  }

  @Override
  public void setSupplyKey(JKey supplyKey) {
    throwIfImmutable("[supplyKey] not settable for immutable instances");
    this.supplyKey = supplyKey;
  }

  @Override
  public void setWipeKey(JKey wipeKey) {
    throwIfImmutable("[wipeKey] not settable for immutable instances");
    this.wipeKey = wipeKey;
  }

  @Override
  public boolean isDeleted() {
    return deleted;
  }

  @Override
  public void setDeleted(boolean deleted) {
    throwIfImmutable("[deleted] not settable for immutable instances");
    this.deleted = deleted;
  }

  @Override
  public boolean isPaused() {
    return paused;
  }

  @Override
  public void setPaused(boolean paused) {
    throwIfImmutable("[paused] not settable for immutable instances");
    this.paused = paused;
  }

  @Override
  public String symbol() {
    return symbol;
  }

  @Override
  public void setSymbol(String symbol) {
    throwIfImmutable("[symbol] not settable for immutable instances");
    this.symbol = symbol;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void setName(String name) {
    throwIfImmutable("[name] not settable for immutable instances");
    this.name = name;
  }

  @Override
  public void setDecimals(int decimals) {
    throwIfImmutable("[decimals] not settable for immutable instances");
    this.decimals = decimals;
  }

  @Override
  public void setTreasury(EntityId treasury) {
    throwIfImmutable("[treasury] not settable for immutable instances");
    this.treasury = treasury;
  }

  @Override
  public void setAdminKey(JKey adminKey) {
    throwIfImmutable("[adminKey] not settable for immutable instances");
    this.adminKey = adminKey;
  }

  @Override
  public boolean accountsAreFrozenByDefault() {
    return accountsFrozenByDefault;
  }

  @Override
  public boolean accountsKycGrantedByDefault() {
    return accountsKycGrantedByDefault;
  }

  @Override
  public EntityId treasury() {
    return treasury;
  }

  @Override
  public EntityNum treasuryNum() {
    return treasury.asNum();
  }

  @Override
  public long expiry() {
    return expiry;
  }

  @Override
  public void setExpiry(long expiry) {
    throwIfImmutable("[expiry] not settable for immutable instances");
    this.expiry = expiry;
  }

  @Override
  public long autoRenewPeriod() {
    return autoRenewPeriod;
  }

  @Override
  public void setAutoRenewPeriod(long autoRenewPeriod) {
    throwIfImmutable("[autoRenewPeriod] not settable for immutable instances");
    this.autoRenewPeriod = autoRenewPeriod;
  }

  @Override
  public EntityId autoRenewAccount() {
    return autoRenewAccount;
  }

  @Override
  public boolean hasAutoRenewAccount() {
    return autoRenewAccount != null;
  }

  @Override
  public void setAutoRenewAccount(EntityId autoRenewAccount) {
    throwIfImmutable("[autoRenewAccount] not settable for immutable instances");
    this.autoRenewAccount = autoRenewAccount;
  }

  @Override
  public TokenID grpcId() {
    return null;  //???
  }

  @Override
  public JKey supplyKey() {
    return supplyKey;
  }

  @Override
  public JKey wipeKey() {
    return wipeKey;
  }

  @Override
  public JKey adminKey() {
    return adminKey;
  }

  @Override
  public JKey kycKey() {
    return kycKey;
  }

  @Override
  public JKey freezeKey() {
    return freezeKey;
  }

  @Override
  public void setTotalSupply(long totalSupply) {
    throwIfImmutable("[totalSupply] not settable for immutable instances");
    this.totalSupply = totalSupply;
  }

  @Override
  public String memo() {
    return memo;
  }

  @Override
  public void setMemo(String memo) {
    throwIfImmutable("[memo] not settable for immutable instances");
    this.memo = memo;
  }

  @Override
  public void setAccountsFrozenByDefault(boolean accountsFrozenByDefault) {
    throwIfImmutable("[accountsFrozenByDefault] not settable for immutable instances");
    this.accountsFrozenByDefault = accountsFrozenByDefault;
  }

  @Override
  public void setAccountsKycGrantedByDefault(boolean accountsKycGrantedByDefault) {
    throwIfImmutable("[accountsKycGrantedByDefault] not settable for immutable instances");
    this.accountsKycGrantedByDefault = accountsKycGrantedByDefault;
  }

  @Override
  public long lastUsedSerialNumber() {
    return lastUsedSerialNumber;
  }

  @Override
  public void setLastUsedSerialNumber(long serialNum) {
    throwIfImmutable("[lastUsedSerialNumber] not settable for immutable instances");
    this.lastUsedSerialNumber = serialNum;
  }

  @Override
  public TokenType tokenType() {
    return TokenType.FUNGIBLE_COMMON;
  }

  @Override
  public void setTokenType(TokenType tokenType) {
    throw new IllegalStateException("Token type [" + tokenType + "] not settable for this class");
  }

  @Override
  public TokenSupplyType supplyType() {
    return supplyType;
  }

  @Override
  public void setSupplyType(TokenSupplyType supplyType) {
    throwIfImmutable("[supplyType] not settable for immutable instances");
    this.supplyType = supplyType;
  }

  @Override
  public long maxSupply() {
    return maxSupply;
  }

  @Override
  public void setMaxSupply(long maxSupply) {
    throwIfImmutable("[maxSupply] not settable for immutable instances");
    this.maxSupply = maxSupply;
  }

  @Override
  public List<FcCustomFee> customFeeSchedule() {
    return feeSchedule;
  }

  @Override
  public void setFeeSchedule(List<FcCustomFee> feeSchedule) {
    throwIfImmutable("[feeSchedule] not settable for immutable instances");
    this.feeSchedule = feeSchedule;
  }

  @Override
  public void setFeeScheduleFrom(List<CustomFee> grpcFeeSchedule) {
    throwIfImmutable("[grpcFeeSchedule] not settable for immutable instances");
//    this.grpcFeeSchedule = grpcFeeSchedule;   //???
  }

  @Override
  public void setFeeScheduleKey(JKey feeScheduleKey) {
    throwIfImmutable("[feeScheduleKey] not settable for immutable instances");
  }

  @Override
  public JKey feeScheduleKey() {
    return feeScheduleKey;
  }

  @Override
  public EntityNum getKey() {
    return null;  //???  which key?
  }

  @Override
  public void setKey(EntityNum entityNum) {
    throwIfImmutable("[entityNum] not settable for immutable instances");
  }

  @Override
  public boolean isImmutable() {
    return immutable; //??? needed??
  }
}
