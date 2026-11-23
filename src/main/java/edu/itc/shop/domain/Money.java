package edu.itc.shop.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    /** Accepts "12.50 EUR". Anything else -> IllegalArgumentException. */
    public static Money parse(String s) {
        if (s == null)
            throw new IllegalArgumentException("Cannot parse Money from null");
        String[] p = s.trim().split("\\s+");
        if (p.length != 2
                || !p[0].matches("(?:0|[1-9]\\d*)(?:\\.\\d+)?")
                || !p[1].matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Cannot parse Money from '" + s + "'");
        }
        try {
            return new Money(new BigDecimal(p[0]), p[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse Money from '" + s + "'");
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}