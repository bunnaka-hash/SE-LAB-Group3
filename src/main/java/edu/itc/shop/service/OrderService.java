package edu.itc.shop.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.OrderDto;
import edu.itc.shop.web.dto.OrderSummary;

/** Task 4 adds markPaid / changeStatus / view to this class. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;

    public OrderService(OrderRepository orders, CustomerRepository customers) {
        this.orders = orders;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(OrderStatus status, Pageable pageable) {
        return orders.findByStatus(status, pageable);
    }

    /** One fetch-join query; DTOs are built while the persistence context is still open. */
    @Transactional(readOnly = true)
    public List<OrderDto> ordersOf(String email) {
        if (!customers.existsByEmail(email)) {
            throw new CustomerNotFoundException(email);
        }
        return orders.findWithLinesByCustomerEmail(email).stream()
                .map(OrderDto::from)
                .toList();
    }
}
