package edu.itc.shop.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderLine;
import edu.itc.shop.domain.OrderStatus;

/** Full order with its lines. Built inside a transaction, so no lazy loading happens in the controller. */
public record OrderDto(Long id, OrderStatus status, Instant placedAt, String customerName,
                       List<Line> lines, BigDecimal total, String currency) {

    public record Line(Long id, String sku, String productName, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {

        static Line from(OrderLine l) {
            Money lineTotal = l.getUnitPrice().times(l.getQuantity());
            return new Line(l.getId(), l.getProduct().getSku(), l.getProduct().getName(),
                    l.getQuantity(), l.getUnitPrice().amount(), lineTotal.amount());
        }
    }

    public static OrderDto from(Order o) {
        Money total = o.total();
        return new OrderDto(o.getId(), o.getStatus(), o.getPlacedAt(), o.getCustomer().getName(),
                o.getLines().stream().map(Line::from).toList(),
                total.amount(), total.currency());
    }
}
