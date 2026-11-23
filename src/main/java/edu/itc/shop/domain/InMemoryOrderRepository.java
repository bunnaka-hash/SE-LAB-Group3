package edu.itc.shop.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentMap<String, Order> orders =
            new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    @Override
    public void delete(String id) {
        orders.remove(id);
    }
}