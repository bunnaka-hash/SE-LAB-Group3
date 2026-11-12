package edu.itc.shop.service;

import java.util.NoSuchElementException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.web.dto.OrderDto;
import edu.itc.shop.web.dto.OrderSummary;
import edu.itc.shop.web.dto.OrderFeedItem;
import edu.itc.shop.web.dto.OrderFeedResponse;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final CustomerRepository customers;

    public OrderService(OrderRepository orders, CustomerRepository customers) {
        this.orders = orders;
        this.customers = customers;
    }

    // ---- Task 3 (restored: lost when Task 4 replaced this file) ----

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(OrderStatus status, Pageable pageable) {
        return orders.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public OrderFeedResponse feed(int size, String after, String strategy) {
        if (size < 1 || size > 100) {
            throw new InvalidFeedRequestException("size must be between 1 and 100");
        }
        Cursor cursor = parseCursor(after);
        if ("slice".equalsIgnoreCase(strategy)) {
            Pageable pageable = Pageable.ofSize(size);
            Slice<OrderSummary> slice = cursor == null
                    ? orders.findByIdIsNotNullOrderByPlacedAtDescIdDesc(pageable)
                    : orders.findFeedAfter(cursor.placedAt(), cursor.id(), pageable);
            List<OrderFeedItem> items = slice.getContent().stream().map(OrderFeedItem::from).toList();
            return response(items, slice.hasNext());
        }
        if (!"keyset".equalsIgnoreCase(strategy)) {
            throw new InvalidFeedRequestException("strategy must be 'slice' or 'keyset'");
        }

        ScrollPosition position = cursor == null
                ? ScrollPosition.keyset()
                : ScrollPosition.forward(Map.of("placedAt", cursor.placedAt(), "id", cursor.id()));
        Window<Order> window = orders.findAllByOrderByPlacedAtDescIdDesc(position, Limit.of(size));
        List<OrderFeedItem> items = window.getContent().stream().map(OrderFeedItem::from).toList();
        return response(items, window.hasNext());
    }

    private static OrderFeedResponse response(List<OrderFeedItem> items, boolean hasNext) {
        String nextCursor = hasNext && !items.isEmpty()
                ? items.getLast().placedAt() + "," + items.getLast().id()
                : null;
        return new OrderFeedResponse(items, nextCursor, hasNext);
    }

    private static Cursor parseCursor(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        String[] parts = after.split(",", -1);
        try {
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new InvalidFeedRequestException("after must be an ISO-8601 timestamp followed by an order id");
        }
    }

    private record Cursor(Instant placedAt, Long id) { }

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

    // ---- Task 4 ----

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