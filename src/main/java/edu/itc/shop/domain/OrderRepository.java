package edu.itc.shop.domain;

import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(String id);

    Order save(Order order);

    void delete(String id);
}