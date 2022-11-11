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
package com.hedera.services.ledger.properties;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.HederaToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public enum TokenProperty implements BeanProperty<HederaToken> {
    TOTAL_SUPPLY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setTotalSupply((long) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::totalSupply;
        }
    },
    ADMIN_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setAdminKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::adminKey;
        }
    },
    FREEZE_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setFreezeKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::freezeKeyUnsafe;
        }
    },
    KYC_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setKycKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::kycKey;
        }
    },
    PAUSE_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setPauseKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::pauseKey;
        }
    },
    SUPPLY_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setSupplyKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::supplyKey;
        }
    },
    FEE_SCHEDULE_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setFeeScheduleKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::feeScheduleKey;
        }
    },
    WIPE_KEY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setWipeKey((JKey) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::wipeKey;
        }
    },
    IS_DELETED {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, f) -> a.setDeleted((boolean) f);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::isDeleted;
        }
    },
    IS_PAUSED {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, f) -> a.setPaused((boolean) f);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::isPaused;
        }
    },
    SYMBOL {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setSymbol((String) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::symbol;
        }
    },
    NAME {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setName((String) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::name;
        }
    },
    TREASURY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setTreasury((EntityId) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::treasury;
        }
    },
    ACC_FROZEN_BY_DEFAULT {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, f) -> a.setAccountsFrozenByDefault((boolean) f);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::accountsAreFrozenByDefault;
        }
    },
    ACC_KYC_GRANTED_BY_DEFAULT {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, f) -> a.setAccountsKycGrantedByDefault((boolean) f);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::accountsKycGrantedByDefault;
        }
    },
    EXPIRY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setExpiry((long) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::expiry;
        }
    },
    AUTO_RENEW_PERIOD {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setAutoRenewPeriod((long) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::autoRenewPeriod;
        }
    },
    AUTO_RENEW_ACCOUNT {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setAutoRenewAccount((EntityId) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::autoRenewAccount;
        }
    },
    MEMO {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setMemo((String) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::memo;
        }
    },
    LAST_USED_SERIAL_NUMBER {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setLastUsedSerialNumber((long) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::lastUsedSerialNumber;
        }
    },
    TOKEN_TYPE {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setTokenType((TokenType) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::tokenType;
        }
    },
    SUPPLY_TYPE {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setSupplyType((TokenSupplyType) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::supplyType;
        }
    },
    MAX_SUPPLY {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setMaxSupply((long) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::maxSupply;
        }
    },
    FEE_SCHEDULE {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setFeeSchedule((List<FcCustomFee>) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::customFeeSchedule;
        }
    },
    DECIMALS {
        @Override
        public BiConsumer<HederaToken, Object> setter() {
            return (a, l) -> a.setDecimals((int) l);
        }

        @Override
        public Function<HederaToken, Object> getter() {
            return HederaToken::decimals;
        }
    }
}
