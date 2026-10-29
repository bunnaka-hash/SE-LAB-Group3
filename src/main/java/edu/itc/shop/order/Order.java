package edu.itc.shop.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A row of the {@code orders} table. */
public record Order(
        Long id,
        long customerId,
        OrderStatus status,
        OffsetDateTime placedAt,
        BigDecimal total
) {}
