package edu.itc.shop.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.audit.Audited;
import edu.itc.shop.common.InvalidRequestException;
import edu.itc.shop.common.Page;
import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.customer.Customer;
import edu.itc.shop.customer.CustomerNotFoundException;
import edu.itc.shop.customer.CustomerRepository;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.service.InsufficientStockException;
import edu.itc.shop.service.Notifier;
import edu.itc.shop.service.ProductNotFoundException;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final ShopProperties props;
    private final Notifier notifier;
    private final Clock clock;

    public OrderService(OrderRepository orders,
                        ProductRepository products,
                        CustomerRepository customers,
                        ShopProperties props,
                        Notifier notifier,
                        Clock clock) {
        this.orders = orders;
        this.products = products;
        this.customers = customers;
        this.props = props;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Reserves the stock of every line and stores the order, all in one transaction:
     * if any line fails, the decrements already done are rolled back.
     */
    @Transactional
    @Audited
    public OrderDto place(PlaceOrder cmd) {
        Customer customer = customers.findById(cmd.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(cmd.customerId()));
        rejectDuplicateProducts(cmd.lines());

        // always by product id: two concurrent orders then lock the product rows in the same
        // order and cannot deadlock; the lines also come back in the order GET returns them
        List<PlaceOrder.Line> sorted = cmd.lines().stream()
                .sorted(Comparator.comparing(PlaceOrder.Line::productId))
                .toList();

        List<OrderLine> lines = new ArrayList<>();
        for (PlaceOrder.Line line : sorted) {
            // check and decrement in one statement: ... WHERE stock + :delta >= 0
            if (products.updateStock(line.productId(), -line.qty()) == 0) {
                Product product = products.findById(line.productId())
                        .orElseThrow(() -> new ProductNotFoundException(line.productId()));
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.id()
                                + ": " + product.stock() + " available, " + line.qty() + " requested");
            }
            Product product = products.findById(line.productId()).orElseThrow();
            lines.add(new OrderLine(product.id(), line.qty(), product.price()));
        }

        BigDecimal total = OrderPricing.total(lines, props.taxRate());
        OffsetDateTime placedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
        Order order = new Order(null, customer.id(), OrderStatus.NEW, placedAt, total);

        long id = orders.insert(order, lines);
        return new OrderDto(id, customer.id(), OrderStatus.NEW, placedAt, total, lines);
    }

    /** Only a NEW order can be cancelled; its stock is returned and the customer is notified. */
    @Transactional
    @Audited
    public OrderDto cancel(long id) {
        // guarded update: of two concurrent cancels only one changes the row
        if (orders.updateStatus(id, OrderStatus.NEW, OrderStatus.CANCELLED) == 0) {
            Order existing = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
            throw new OrderNotCancellableException(id, existing.status());
        }

        Order order = orders.findById(id).orElseThrow();
        List<OrderLine> lines = orders.findLines(id);
        for (OrderLine line : lines) {
            products.updateStock(line.productId(), line.qty());
        }

        customers.findById(order.customerId()).ifPresent(customer ->
                notifier.send(customer.email(), "Your order " + id + " was cancelled. "
                        + order.total() + " " + props.currency() + " will be refunded."));
        return OrderDto.from(order, lines);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> list(Long customerId, OrderStatus status, int page, int size) {
        long totalElements = orders.count(customerId, status);
        List<Order> found = orders.findPage(customerId, status, size, (long) page * size);
        Map<Long, List<OrderLine>> lines = orders.findLines(found.stream().map(Order::id).toList());

        List<OrderDto> content = found.stream()
                .map(order -> OrderDto.from(order, lines.getOrDefault(order.id(), List.of())))
                .toList();
        return Page.of(content, page, size, totalElements);
    }

    @Transactional(readOnly = true)
    public OrderDto findById(long id) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        return OrderDto.from(order, orders.findLines(id));
    }

    private static void rejectDuplicateProducts(List<PlaceOrder.Line> lines) {
        Set<Long> seen = new HashSet<>();
        for (PlaceOrder.Line line : lines) {
            if (!seen.add(line.productId())) {
                throw new InvalidRequestException(
                        "Product " + line.productId() + " appears more than once; use one line with the total quantity");
            }
        }
    }
}
