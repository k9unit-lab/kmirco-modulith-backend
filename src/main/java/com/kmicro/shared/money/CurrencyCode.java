package com.kmicro.shared.money;

import java.util.Currency;
import java.util.Objects;

/** An ISO 4217 currency code such as {@code INR}, checked with {@link Currency}. */
public record CurrencyCode(String code) {

    public CurrencyCode {
        Objects.requireNonNull(code, "code");
        Currency currency;
        try {
            currency = Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an ISO 4217 currency code: " + code, e);
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Currency has no fixed decimals: " + code);
        }
    }

    public static CurrencyCode of(String code) {
        return new CurrencyCode(code);
    }

    /** How many decimals this currency uses: 2 for INR and USD, 0 for JPY. */
    public int decimals() {
        return Currency.getInstance(code).getDefaultFractionDigits();
    }

    @Override
    public String toString() {
        return code;
    }
}
