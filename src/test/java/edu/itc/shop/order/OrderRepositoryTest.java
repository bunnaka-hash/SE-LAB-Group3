package edu.itc.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** Repository slice on H2 with data-test.sql; every test is rolled back. */
@JdbcTest
@ActiveProfiles("test")
@Import(OrderRepository.class)
class OrderRepositoryTest {

    @Autowired
    OrderRepository repository;

    @Test
    void insertStoresOrderAndLines() {
        var placedAt = OffsetDateTime.of(2026, 10, 29, 9, 6, 34, 640_146_000, ZoneOffset.UTC);
        var lines = List.of(new OrderLine(1L, 2, new BigDecimal("49.90")),
                new OrderLine(3L, 1, new BigDecimal("10.00")));

        long id = repository.insert(new Order(null, 2L, OrderStatus.NEW, placedAt, new BigDecimal("120.78")), lines);

        Order found = repository.findById(id).orElseThrow();
        assertThat(found.customerId()).isEqualTo(2L);
        assertThat(found.status()).isEqualTo(OrderStatus.NEW);
        assertThat(found.placedAt().toInstant()).isEqualTo(placedAt.toInstant());
        assertThat(found.total()).isEqualByComparingTo("120.78");
        assertThat(repository.findLines(id)).containsExactlyElementsOf(lines);
    }

    @Test
    void findByIdOfUnknownOrderIsEmpty() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findPageIsNewestFirstWithLimitAndOffset() {
        assertThat(repository.findPage(null, null, 2, 0)).extracting(Order::id).containsExactly(3L, 2L);
        assertThat(repository.findPage(null, null, 2, 2)).extracting(Order::id).containsExactly(1L);
        assertThat(repository.findPage(null, null, 2, 4)).isEmpty();
    }

    @Test
    void findPageFiltersByCustomerAndStatus() {
        assertThat(repository.findPage(1L, null, 10, 0)).extracting(Order::id).containsExactly(2L, 1L);
        assertThat(repository.findPage(1L, OrderStatus.PAID, 10, 0)).extracting(Order::id).containsExactly(1L);
        assertThat(repository.findPage(null, OrderStatus.CANCELLED, 10, 0)).extracting(Order::id).containsExactly(3L);
        assertThat(repository.findPage(3L, null, 10, 0)).isEmpty();
    }

    @Test
    void countUsesTheSameFilters() {
        assertThat(repository.count(null, null)).isEqualTo(3);
        assertThat(repository.count(1L, null)).isEqualTo(2);
        assertThat(repository.count(1L, OrderStatus.NEW)).isEqualTo(1);
        assertThat(repository.count(2L, OrderStatus.NEW)).isZero();
    }

    @Test
    void findLinesOfSeveralOrdersGroupsThemByOrder() {
        var byOrder = repository.findLines(List.of(1L, 3L));

        assertThat(byOrder).containsOnlyKeys(1L, 3L);
        assertThat(byOrder.get(1L)).extracting(OrderLine::productId).containsExactly(1L, 3L);
        assertThat(byOrder.get(3L)).containsExactly(new OrderLine(5L, 1, new BigDecimal("9.95")));
        assertThat(repository.findLines(List.of())).isEmpty();
    }

    @Test
    void updateStatusOnlyChangesARowInTheExpectedStatus() {
        assertThat(repository.updateStatus(2L, OrderStatus.NEW, OrderStatus.CANCELLED)).isEqualTo(1);
        assertThat(repository.findById(2L).orElseThrow().status()).isEqualTo(OrderStatus.CANCELLED);

        // already cancelled / paid / unknown: nothing changes
        assertThat(repository.updateStatus(2L, OrderStatus.NEW, OrderStatus.CANCELLED)).isZero();
        assertThat(repository.updateStatus(1L, OrderStatus.NEW, OrderStatus.CANCELLED)).isZero();
        assertThat(repository.updateStatus(999L, OrderStatus.NEW, OrderStatus.CANCELLED)).isZero();
    }

    @Test
    void orderForUnknownCustomerViolatesForeignKey() {
        var order = new Order(null, 999L, OrderStatus.NEW, OffsetDateTime.now(ZoneOffset.UTC), BigDecimal.ONE);

        assertThatThrownBy(() -> repository.insert(order, List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
