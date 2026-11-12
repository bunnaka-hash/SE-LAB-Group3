package edu.itc.shop.web.dto;

import java.time.Instant;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;

public record OrderFeedItem(Long id, OrderStatus status, Instant placedAt, String customerName) {
    public static OrderFeedItem from(Order order) {
        return new OrderFeedItem(order.getId(), order.getStatus(), order.getPlacedAt(),
                order.getCustomer().getName());
    }

    public static OrderFeedItem from(OrderSummary summary) {
        return new OrderFeedItem(summary.getId(), summary.getStatus(), summary.getPlacedAt(),
                summary.getCustomer().getName());
    }
}
