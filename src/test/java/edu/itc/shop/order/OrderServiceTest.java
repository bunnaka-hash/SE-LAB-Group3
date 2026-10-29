package edu.itc.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

/** Unit test of the order rules with Mockito; no Spring context, no database. */
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T09:00:00Z");
    private static final Customer SOKHA = new Customer(1L, "Sokha Chan", "sokha@example.com");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final Notifier notifier = mock(Notifier.class);

    private OrderService service;

    @BeforeEach
    void setUp() {
        var props = new ShopProperties("USD", new BigDecimal("0.10"), 5);
        service = new OrderService(orders, products, customers, props, notifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(customers.findById(1L)).thenReturn(Optional.of(SOKHA));
    }

    private static PlaceOrder order(PlaceOrder.Line... lines) {
        return new PlaceOrder(1L, List.of(lines));
    }

    private static PlaceOrder.Line line(long productId, int qty) {
        return new PlaceOrder.Line(productId, qty);
    }

    @Test
    void placeReservesStockSnapshotsPriceAndComputesTotal() {
        when(products.updateStock(1L, -2)).thenReturn(1);
        when(products.updateStock(3L, -1)).thenReturn(1);
        when(products.findById(1L)).thenReturn(Optional.of(new Product(1L, "Mouse", new BigDecimal("49.90"), 8)));
        when(products.findById(3L)).thenReturn(Optional.of(new Product(3L, "Pad", new BigDecimal("10.00"), 19)));
        when(orders.insert(any(), any())).thenReturn(17L);

        OrderDto dto = service.place(order(line(1, 2), line(3, 1)));

        assertThat(dto.id()).isEqualTo(17L);
        assertThat(dto.status()).isEqualTo(OrderStatus.NEW);
        assertThat(dto.placedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(dto.total()).isEqualTo(new BigDecimal("120.78"));   // 109.80 x 1.10
        assertThat(dto.lines()).containsExactly(
                new OrderLine(1L, 2, new BigDecimal("49.90")),
                new OrderLine(3L, 1, new BigDecimal("10.00")));

        ArgumentCaptor<Order> stored = ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(stored.capture(), eq(dto.lines()));
        assertThat(stored.getValue().customerId()).isEqualTo(1L);
        assertThat(stored.getValue().total()).isEqualTo(new BigDecimal("120.78"));
    }

    @Test
    void sameProductTwiceIsRejectedBeforeTouchingStock() {
        assertThatThrownBy(() -> service.place(order(line(1, 2), line(1, 3))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Product 1 appears more than once");

        verifyNoInteractions(products, orders);
    }

    @Test
    void unknownCustomerIsRejected() {
        when(customers.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place(new PlaceOrder(9L, List.of(line(1, 1)))))
                .isInstanceOf(CustomerNotFoundException.class);

        verifyNoInteractions(products, orders);
    }

    @Test
    void lineWithoutEnoughStockThrowsAndNoOrderIsStored() {
        when(products.updateStock(1L, -2)).thenReturn(1);
        when(products.findById(1L)).thenReturn(Optional.of(new Product(1L, "Mouse", new BigDecimal("49.90"), 8)));
        when(products.updateStock(2L, -999)).thenReturn(0);
        when(products.findById(2L)).thenReturn(Optional.of(new Product(2L, "Cable", new BigDecimal("7.50"), 150)));

        assertThatThrownBy(() -> service.place(order(line(1, 2), line(2, 999))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for product 2: 150 available, 999 requested");

        // the decrement of line 1 is undone by the transaction rollback, not by the service
        verify(orders, never()).insert(any(), any());
    }

    @Test
    void unknownProductIsNotFound() {
        when(products.updateStock(42L, -1)).thenReturn(0);
        when(products.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place(order(line(42, 1))))
                .isInstanceOf(ProductNotFoundException.class);
        verify(orders, never()).insert(any(), any());
    }

    @Test
    void cancelNewOrderReturnsStockAndNotifiesCustomer() {
        var cancelled = new Order(7L, 1L, OrderStatus.CANCELLED,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), new BigDecimal("120.78"));
        var lines = List.of(new OrderLine(1L, 2, new BigDecimal("49.90")),
                new OrderLine(3L, 1, new BigDecimal("10.00")));
        when(orders.updateStatus(7L, OrderStatus.NEW, OrderStatus.CANCELLED)).thenReturn(1);
        when(orders.findById(7L)).thenReturn(Optional.of(cancelled));
        when(orders.findLines(7L)).thenReturn(lines);

        OrderDto dto = service.cancel(7L);

        assertThat(dto.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(products).updateStock(1L, 2);
        verify(products).updateStock(3L, 1);
        verify(notifier).send(eq("sokha@example.com"), anyString());
    }

    @Test
    void cancelOrderThatIsNotNewIsConflictAndChangesNothing() {
        when(orders.updateStatus(5L, OrderStatus.NEW, OrderStatus.CANCELLED)).thenReturn(0);
        when(orders.findById(5L)).thenReturn(Optional.of(new Order(5L, 1L, OrderStatus.PAID,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), BigDecimal.TEN)));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessage("Order 5 is PAID; only NEW orders can be cancelled");

        verify(products, never()).updateStock(anyLong(), anyInt());
        verifyNoInteractions(notifier);
    }

    @Test
    void cancelUnknownOrderIsNotFound() {
        when(orders.updateStatus(99L, OrderStatus.NEW, OrderStatus.CANCELLED)).thenReturn(0);
        when(orders.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(notifier);
    }

    @Test
    void listUsesOffsetAndCountsPages() {
        var order = new Order(4L, 1L, OrderStatus.NEW, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), BigDecimal.ONE);
        when(orders.count(1L, OrderStatus.NEW)).thenReturn(5L);
        when(orders.findPage(1L, OrderStatus.NEW, 2, 4L)).thenReturn(List.of(order));
        when(orders.findLines(List.of(4L))).thenReturn(Map.of(4L, List.of(new OrderLine(2L, 1, BigDecimal.ONE))));

        Page<OrderDto> page = service.list(1L, OrderStatus.NEW, 2, 2);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).singleElement()
                .satisfies(dto -> assertThat(dto.lines()).hasSize(1));
    }
}
