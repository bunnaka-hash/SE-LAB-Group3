package edu.itc.shop.domain;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;

    private final AtomicInteger sequence =
            new AtomicInteger(1000);

    @Inject
    public OrderService(ProductRepository products,
                        OrderRepository orders) {

        this.products = products;
        this.orders = orders;
    }

    public synchronized Order place(PlaceOrder cmd) {
        if (cmd == null || cmd.lines() == null || cmd.lines().isEmpty()) {
            throw new BadRequestException("At least one order line is required");
        }

        List<Line> checkedLines = new ArrayList<>(cmd.lines().size());
        Map<Long, Integer> quantitiesByProduct = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Line line : cmd.lines()) {
            if (line == null) {
                throw new BadRequestException("Order line cannot be null");
            }
            if (line.getQuantity() < 1) {
                throw new BadRequestException("Order line quantity must be at least 1");
            }

            Product product = products.findById(line.getProductId())
                    .orElseThrow(() -> new NotFoundException(
                            "Product " + line.getProductId() + " not found"));

            checkedLines.add(new Line(product.id(), line.getQuantity()));
            int aggregateQuantity = quantitiesByProduct.merge(
                    product.id(), line.getQuantity(), Integer::sum);
            if (product.stock() < aggregateQuantity) {
                throw new InsufficientStockException(
                        product.id(), product.stock(), aggregateQuantity);
            }
            total = total.add(product.price()
                    .multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        for (Line line : checkedLines) {
            Product product = products.findById(line.getProductId()).orElseThrow();
            products.save(new Product(
                    product.id(),
                    product.name(),
                    product.sku(),
                    product.price(),
                    product.stock() - line.getQuantity(),
                    product.version()));
        }

        Order order = new Order(
                "o-" + sequence.incrementAndGet(),
                List.copyOf(checkedLines),
                total,
                OrderStatus.NEW);
        return orders.save(order);
    }

    public Order get(String id) {

        return orders.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Order " + id + " not found"));
    }

    public synchronized Order pay(String id) {

        Order order = get(id);
        requireStatus(order, OrderStatus.NEW);

        Order paid =
                new Order(
                        order.id(),
                        order.lines(),
                        order.total(),
                        OrderStatus.PAID
                );

        return orders.save(paid);
    }

    public synchronized void cancel(String id) {

        Order order = get(id);
        requireStatus(order, OrderStatus.NEW);

        Order cancelled =
                new Order(
                        order.id(),
                        order.lines(),
                        order.total(),
                        OrderStatus.CANCELLED
                );

        orders.save(cancelled);

        for (Line line : order.lines()) {
            Product product = products.findById(line.getProductId()).orElseThrow();
            products.save(new Product(
                    product.id(),
                    product.name(),
                    product.sku(),
                    product.price(),
                    product.stock() + line.getQuantity(),
                    product.version()));
        }
    }

    private static void requireStatus(Order order, OrderStatus required) {
        if (order.status() != required) {
            throw new ClientErrorException(
                    "Order " + order.id() + " cannot be changed from " + order.status(),
                    Response.Status.CONFLICT);
        }
    }
}