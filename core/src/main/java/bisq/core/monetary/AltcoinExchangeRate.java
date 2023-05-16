/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.monetary;

import org.bitcoinj.core.Coin;

import java.math.BigInteger;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

// Cloned from ExchangeRate. Use Altcoin instead of Fiat.
@Slf4j
public class AltcoinExchangeRate {
    /**
     * An exchange rate is expressed as a ratio of a {@link Coin} and a {@link Altcoin} amount.
     */

    public final Coin coin;
    public final Altcoin altcoin;

    /**
     * Construct exchange rate. This amount of coin is worth that amount of altcoin.
     */
    @SuppressWarnings("SameParameterValue")
    public AltcoinExchangeRate(Coin coin, Altcoin altcoin) {
        checkArgument(coin.isPositive());
        checkArgument(altcoin.isPositive());
        checkArgument(altcoin.currencyCode != null, "currency code required");
        this.coin = coin;
        this.altcoin = altcoin;
    }

    /**
     * Construct exchange rate. One coin is worth this amount of altcoin.
     */
    public AltcoinExchangeRate(Altcoin altcoin) {
        this(Coin.COIN, altcoin);
    }

    /**
     * Convert a coin amount to an altcoin amount using this exchange rate.
     *
     * @throws ArithmeticException if the converted altcoin amount is too high or too low.
     */
    public Altcoin coinToAltcoin(Coin convertCoin) {
        long converted;
        if ((int) coin.value == coin.value && (int) convertCoin.value == convertCoin.value) {
            // Short circuit in a common case where long arithmetic won't overflow.
            converted = coin.value * convertCoin.value / altcoin.value;
        } else {
            // Otherwise use BigInteger to maintain full precision without overflowing.
            converted = BigInteger.valueOf(coin.value)
                    .multiply(BigInteger.valueOf(convertCoin.value))
                    .divide(BigInteger.valueOf(altcoin.value))
                    .longValueExact();
        }
        return Altcoin.valueOf(altcoin.currencyCode, converted);
    }

    /**
     * Convert an altcoin amount to a coin amount using this exchange rate.
     *
     * @throws ArithmeticException if the converted coin amount is too high or too low.
     */
    public Coin altcoinToCoin(Altcoin convertAltcoin) {
        checkArgument(convertAltcoin.currencyCode.equals(altcoin.currencyCode), "Currency mismatch: %s vs %s",
                convertAltcoin.currencyCode, altcoin.currencyCode);
        long converted;
        if ((int) altcoin.value == altcoin.value && (int) convertAltcoin.value == convertAltcoin.value) {
            // Short circuit in a common case where long arithmetic won't overflow.
            converted = altcoin.value * convertAltcoin.value / coin.value;
        } else {
            // Otherwise use BigInteger to maintain full precision without overflowing.
            converted = BigInteger.valueOf(altcoin.value)
                    .multiply(BigInteger.valueOf(convertAltcoin.value))
                    .divide(BigInteger.valueOf(coin.value))
                    .longValueExact();
        }
        return Coin.valueOf(converted);
    }
}
