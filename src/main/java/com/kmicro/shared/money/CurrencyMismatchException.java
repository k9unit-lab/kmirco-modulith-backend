package com.kmicro.shared.money;

import java.io.Serial;

/** Thrown when two amounts in different currencies are added or subtracted. */
public class CurrencyMismatchException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(CurrencyCode left, CurrencyCode right) {
        super("Cannot mix currencies: " + left + " and " + right);
    }
}
