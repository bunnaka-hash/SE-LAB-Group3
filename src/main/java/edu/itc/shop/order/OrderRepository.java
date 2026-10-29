package edu.itc.shop.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private static final String ORDER_COLUMNS = "SELECT id, customer_id, status, placed_at, total FROM orders";

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the order and its lines; returns the new order id. */
    public long insert(Order order, List<OrderLine> lines) {
        var keys = new GeneratedKeyHolder();

        // name the key column: the PostgreSQL driver otherwise returns every column as a key
        jdbc.sql("""
                INSERT INTO orders(customer_id, status, placed_at, total)
                VALUES (:customerId, :status, :placedAt, :total)
                """)
                .param("customerId", order.customerId())
                .param("status", order.status().name())
                .param("placedAt", order.placedAt())
                .param("total", order.total())
                .update(keys, "id");

        if (keys.getKey() == null) {
            throw new IllegalStateException("No generated order ID returned");
        }
        long id = keys.getKey().longValue();

        for (OrderLine line : lines) {
            jdbc.sql("""
                    INSERT INTO order_line(order_id, product_id, qty, unit_price)
                    VALUES (:orderId, :productId, :qty, :unitPrice)
                    """)
                    .param("orderId", id)
                    .param("productId", line.productId())
                    .param("qty", line.qty())
                    .param("unitPrice", line.unitPrice())
                    .update();
        }
        return id;
    }

    public Optional<Order> findById(long id) {
        return jdbc.sql(ORDER_COLUMNS + " WHERE id = :id")
                .param("id", id)
                .query(OrderRepository::mapOrder)
                .optional();
    }

    public List<OrderLine> findLines(long orderId) {
        return jdbc.sql("""
                SELECT product_id, qty, unit_price
                FROM order_line WHERE order_id = :orderId
                ORDER BY product_id
                """)
                .param("orderId", orderId)
                .query(OrderRepository::mapLine)
                .list();
    }

    /** Lines of several orders in one query, grouped by order id. */
    public Map<Long, List<OrderLine>> findLines(Collection<Long> orderIds) {
        Map<Long, List<OrderLine>> byOrder = new LinkedHashMap<>();
        if (orderIds.isEmpty()) {
            return byOrder;
        }
        jdbc.sql("""
                SELECT order_id, product_id, qty, unit_price
                FROM order_line WHERE order_id IN (:orderIds)
                ORDER BY order_id, product_id
                """)
                .param("orderIds", orderIds)
                .query((rs, rowNum) -> Map.entry(rs.getLong("order_id"), mapLine(rs, rowNum)))
                .list()
                .forEach(e -> byOrder.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return byOrder;
    }

    /** One page, newest first. Both filters are optional (null = no filter). */
    public List<Order> findPage(Long customerId, OrderStatus status, int limit, long offset) {
        return jdbc.sql(ORDER_COLUMNS + where(customerId, status)
                        + " ORDER BY placed_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(filterParams(customerId, status))
                .param("limit", limit)
                .param("offset", offset)
                .query(OrderRepository::mapOrder)
                .list();
    }

    public long count(Long customerId, OrderStatus status) {
        return jdbc.sql("SELECT COUNT(*) FROM orders" + where(customerId, status))
                .params(filterParams(customerId, status))
                .query(Long.class)
                .single();
    }

    /** Changes the status only if it is still {@code from}; returns the number of rows changed (0 or 1). */
    public int updateStatus(long id, OrderStatus from, OrderStatus to) {
        return jdbc.sql("UPDATE orders SET status = :to WHERE id = :id AND status = :from")
                .param("id", id)
                .param("from", from.name())
                .param("to", to.name())
                .update();
    }

    // only fixed SQL fragments are added here; the values themselves are always bound parameters
    private static String where(Long customerId, OrderStatus status) {
        List<String> conditions = new ArrayList<>();
        if (customerId != null) {
            conditions.add("customer_id = :customerId");
        }
        if (status != null) {
            conditions.add("status = :status");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static Map<String, Object> filterParams(Long customerId, OrderStatus status) {
        Map<String, Object> params = new HashMap<>();
        if (customerId != null) {
            params.put("customerId", customerId);
        }
        if (status != null) {
            params.put("status", status.name());
        }
        return params;
    }

    private static Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new Order(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getObject("placed_at", OffsetDateTime.class),
                rs.getBigDecimal("total"));
    }

    private static OrderLine mapLine(ResultSet rs, int rowNum) throws SQLException {
        return new OrderLine(rs.getLong("product_id"), rs.getInt("qty"), rs.getBigDecimal("unit_price"));
    }
}
