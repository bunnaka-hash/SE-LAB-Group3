package edu.itc.shop.web;

import edu.itc.shop.domain.Order;
import edu.itc.shop.service.OrderService;
import edu.itc.shop.web.dto.PlaceOrder;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Customer places an order
    @PostMapping
    public ResponseEntity<Order> place(
            @Valid @RequestBody PlaceOrder cmd,
            Authentication authentication) {

        Order order = orderService.place(authentication.getName(), cmd);

        return ResponseEntity
                .created(URI.create("/api/orders/" + order.getId()))
                .body(order);
    }

    // Customer sees own history; ADMIN can specify another customer
    @GetMapping
    public List<Order> history(
            @RequestParam(required = false) String customer,
            Authentication authentication) {

        String customerId = (customer == null || customer.isBlank())
                ? authentication.getName()
                : customer;

        return orderService.history(customerId);
    }

    // Get one order
    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
        return orderService.get(id);
    }

    // ADMIN ships an order
    @PostMapping("/{id}/ship")
    public ResponseEntity<Void> ship(@PathVariable long id) {
        orderService.ship(id);
        return ResponseEntity.noContent().build();
    }
}