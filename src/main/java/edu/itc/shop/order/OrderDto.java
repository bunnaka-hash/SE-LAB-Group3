package edu.itc.shop.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDto(
        long id,
        long customerId,
        OrderStatus status,
        OffsetDateTime placedAt,
        BigDecimal total,
        List<OrderLine> lines
) {
    public static OrderDto from(Order order, List<OrderLine> lines) {
        return new OrderDto(order.id(), order.customerId(), order.status(),
                order.placedAt(), order.total(), lines);
    }
}
