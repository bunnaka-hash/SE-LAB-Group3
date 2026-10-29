package edu.itc.shop.order;

import java.math.BigDecimal;

/** A row of {@code order_line}; {@code unitPrice} is the product price when the order was placed. */
public record OrderLine(long productId, int qty, BigDecimal unitPrice) {}
