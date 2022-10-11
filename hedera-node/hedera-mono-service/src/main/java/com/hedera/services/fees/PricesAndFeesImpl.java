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
package com.hedera.services.fees;

import static com.hederahashgraph.fee.FeeBuilder.FEE_DIVISOR_FACTOR;
import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.function.ToLongFunction;
import javax.inject.Inject;

public class PricesAndFeesImpl implements PricesAndFeesProvider {

    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;
    private final FeeMultiplierSource feeMultiplierSource;
    private final TransactionContext txnCtx;

    @Inject
    public PricesAndFeesImpl(
            final HbarCentExchange exchange,
            final UsagePricesProvider usagePrices,
            final FeeMultiplierSource feeMultiplierSource,
            final TransactionContext txnCtx) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.feeMultiplierSource = feeMultiplierSource;
        this.txnCtx = txnCtx;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return usagePrices.defaultPricesGiven(function, at);
    }

    @Override
    public ExchangeRate rate(Timestamp at) {
        return exchange.rate(at);
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        return estimatedGasPrice(function, at);
    }

    @Override
    public long currentGasPrice(Instant now, HederaFunctionality function) {
        return currentPrice(MiscUtils.asTimestamp(now), function, FeeComponents::getGas);
    }

    private long estimatedGasPrice(HederaFunctionality function, Timestamp at) {
        var rates = exchange.rate(at);
        var prices = usagePrices.defaultPricesGiven(function, at);
        return gasPriceInTinybars(prices, rates);
    }

    private long gasPriceInTinybars(FeeData prices, ExchangeRate rates) {
        long priceInTinyCents = prices.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
        long priceInTinyBars = getTinybarsFromTinyCents(rates, priceInTinyCents);
        return Math.max(priceInTinyBars, 1L);
    }

    public long currentMultiplier(TxnAccessor accessor) {
        return feeMultiplierSource.currentMultiplier(accessor);
    }

    public long currentGasPriceInTinycents(
            final Timestamp now, final HederaFunctionality function) {
        return currentFeeInTinycents(now, function, FeeComponents::getGas);
    }

    public long currentStorageByteHoursPrice(
            final Timestamp now, final HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getSbh);
    }

    private long currentFeeInTinycents(
            final Timestamp now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var prices = usagePrices.defaultPricesGiven(function, now);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }

    private long currentPrice(
            final Timestamp now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var timestamp = Timestamp.newBuilder().setSeconds(now.getSeconds()).build();
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);

        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
        final var curMultiplier = feeMultiplierSource.currentMultiplier(txnCtx.accessor());
        if (curMultiplier > maxMultiplier) {
            return Long.MAX_VALUE;
        } else {
            return unscaledPrice * curMultiplier;
        }
    }
}
