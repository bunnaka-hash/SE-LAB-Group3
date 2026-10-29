package edu.itc.shop.order;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.itc.shop.common.Page;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderDto> place(@RequestBody @Valid PlaceOrder cmd) {
        OrderDto created = service.place(cmd);
        return ResponseEntity
                .created(URI.create("/api/orders/" + created.id()))
                .body(created);
    }

    @GetMapping
    public Page<OrderDto> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(customerId, status, page, size);
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
