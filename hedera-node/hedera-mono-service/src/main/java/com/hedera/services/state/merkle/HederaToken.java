/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.Mutable;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.util.ArrayList;
import java.util.List;

public interface HederaToken extends FastCopyable, Mutable {
  JKey UNUSED_KEY = null;
  long UNUSED_AUTO_RENEW_PERIOD = -1L;

  long totalSupply();

  int decimals();

  JKey freezeKeyUnsafe();

  JKey pauseKey();

  void setPauseKey(JKey pauseKey);

  void setFreezeKey(JKey freezeKey);

  void setKycKey(JKey kycKey);

  void setSupplyKey(JKey supplyKey);

  void setWipeKey(JKey wipeKey);

  boolean isDeleted();

  void setDeleted(boolean deleted);

  boolean isPaused();

  void setPaused(boolean paused);

  String symbol();

  void setSymbol(String symbol);

  String name();

  void setName(String name);

  void setDecimals(int decimals);

  void setTreasury(EntityId treasury);

  void setAdminKey(JKey adminKey);

  boolean accountsAreFrozenByDefault();

  boolean accountsKycGrantedByDefault();

  EntityId treasury();

  EntityNum treasuryNum();

  long expiry();

  void setExpiry(long expiry);

  long autoRenewPeriod();

  void setAutoRenewPeriod(long autoRenewPeriod);

  EntityId autoRenewAccount();

  boolean hasAutoRenewAccount();

  void setAutoRenewAccount(EntityId autoRenewAccount);

  TokenID grpcId();

  JKey supplyKey();

  JKey wipeKey();

  JKey adminKey();

  JKey kycKey();

  JKey freezeKey();

  void setTotalSupply(long totalSupply);

  String memo();

  void setMemo(String memo);

  void setAccountsFrozenByDefault(boolean accountsFrozenByDefault);

  void setAccountsKycGrantedByDefault(boolean accountsKycGrantedByDefault);

  long lastUsedSerialNumber();

  void setLastUsedSerialNumber(long serialNum);

  TokenType tokenType();

  void setTokenType(TokenType tokenType);

  TokenSupplyType supplyType();

  void setSupplyType(TokenSupplyType supplyType);

  long maxSupply();

  void setMaxSupply(long maxSupply);

  List<FcCustomFee> customFeeSchedule();

  void setFeeSchedule(List<FcCustomFee> feeSchedule);

  void setFeeScheduleFrom(List<CustomFee> grpcFeeSchedule);

  void setFeeScheduleKey(final JKey feeScheduleKey);

  JKey feeScheduleKey();

  default boolean hasAdminKey() {
    return isNotUnusedKey(adminKey());
  }

  default boolean hasFreezeKey() {
    return isNotUnusedKey(freezeKey());
  }

  default boolean hasKycKey() {
    return isNotUnusedKey(kycKey());
  }

  default boolean hasPauseKey() {
    return isNotUnusedKey(pauseKey());
  }

  default boolean hasSupplyKey() {
    return isNotUnusedKey(supplyKey());
  }

  default boolean hasWipeKey() {
    return isNotUnusedKey(wipeKey());
  }

  default List<CustomFee> grpcFeeSchedule() {
    final List<CustomFee> grpcList = new ArrayList<>();
    for (var customFee : customFeeSchedule()) {
      grpcList.add(customFee.asGrpc());
    }
    return grpcList;
  }

  default boolean hasFeeScheduleKey() {
    return isNotUnusedKey(feeScheduleKey());
  }

  default boolean isNotUnusedKey(JKey key) {
    return key != UNUSED_KEY;
  }
}
