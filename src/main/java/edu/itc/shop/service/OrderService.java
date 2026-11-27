package edu.itc.shop.service;

import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.PlaceOrder;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    @PreAuthorize("#customerId == authentication.name")
    @Transactional
    public Order place(String customerId, PlaceOrder cmd) {
        return orders.save(
            new Order(customerId, cmd.productId(), cmd.quantity())
        );
    }

    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.name")
    public List<Order> history(String customerId) {
        return orders.findByCustomer(customerId);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.customer == authentication.name")
    public Order get(long id) {
        return orders.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void ship(long id) {
        Order order = orders.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found"));

        order.ship();
        orders.save(order);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> all() {
        return orders.findAll();
    }
}