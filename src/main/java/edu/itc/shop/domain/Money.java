package edu.itc.shop.domain;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {

    /** Accepts "12.50 EUR". Anything else throws IllegalArgumentException. */
    public static Money parse(String s) {
        if (s == null)
            throw new IllegalArgumentException("Cannot parse Money from null");
        String[] parts = s.trim().split("\\s+");
        if (parts.length != 2 || !parts[1].matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Cannot parse Money from '" + s + "'");
        }
        try {
            return new Money(new BigDecimal(parts[0]), parts[1].toUpperCase());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse Money from '" + s + "'", e);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}