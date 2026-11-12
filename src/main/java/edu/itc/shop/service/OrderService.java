package edu.itc.shop.service;

import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.OrderDto;

@Service
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) { this.orders = orders; }

    /** Managed entity: no save() needed, dirty checking issues the UPDATE at commit. */
    @Transactional
    public void markPaid(Long orderId) {
        Order o = orders.findById(orderId).orElseThrow(() -> notFound(orderId));
        o.setStatus(OrderStatus.PAID);
    }

    /** Same idea with an explicit target status. Used by the locking tests. */
    @Transactional
    public void changeStatus(Long orderId, OrderStatus next) {
        Order o = orders.findById(orderId).orElseThrow(() -> notFound(orderId));
        o.setStatus(next);
    }

    @Transactional(readOnly = true)
    public OrderDto view(Long orderId) {
        Order o = orders.findById(orderId).orElseThrow(() -> notFound(orderId));
        return OrderDto.from(o); // lines are read inside the transaction
    }

    /**
     * THE TRAP (keep it as documentation): the entity is modified, but under readOnly = true
     * Spring/Hibernate use FlushMode.MANUAL, so nothing is flushed and nothing is written.
     */
    @Transactional(readOnly = true)
    public void wrongReadOnlyChange(Long orderId, OrderStatus next) {
        Order o = orders.findById(orderId).orElseThrow(() -> notFound(orderId));
        o.setStatus(next);
    }

    private static NoSuchElementException notFound(Long id) {
        return new NoSuchElementException("Order " + id + " not found");
    }
}