package edu.itc.shop.repository;

import java.util.List;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import edu.itc.shop.domain.*;
import edu.itc.shop.web.dto.OrderSummary;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)   // real Flyway schema + ddl-auto=validate
class OrderRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;

    Long paidOrderId;

    @BeforeEach
    void seed() {
        Address a = new Address("12 St James Sq", "London", "SW1Y");
        Customer ada = em.persist(new Customer("Ada Lovelace", "ada@example.com", a, a));

        Product kb   = new Product("KB-1", "Office keyboard",  Money.eur("24.90"), Category.HARDWARE, 40);
        Product book = new Product("BK-2", "Java Persistence", Money.eur("54.00"), Category.BOOKS,     2);
        em.persist(kb);
        em.persist(book);

        Order paid = new Order(ada);
        paid.addLine(kb, 2);
        paid.addLine(book, 1);
        paid.setStatus(OrderStatus.PAID);
        em.persist(paid);

        Order fresh = new Order(ada);
        fresh.addLine(kb, 1);
        em.persist(fresh);

        em.flush();
        em.clear();          // every test starts with an empty persistence context
        paidOrderId = paid.getId();
    }

    @Test
    void derivedQueries() {
        List<Order> result = orders.findByCustomerEmailAndStatusOrderByPlacedAtDesc(
                "ada@example.com", OrderStatus.PAID);
        assertThat(result).extracting(Order::getId).containsExactly(paidOrderId);

        assertThat(orders.countByStatus(OrderStatus.PAID)).isEqualTo(1);
        assertThat(orders.countByStatus(OrderStatus.NEW)).isEqualTo(1);
        assertThat(orders.countLinesByStatus(OrderStatus.PAID)).isEqualTo(2);   // JPQL aggregate
        assertThat(products.existsBySku("KB-1")).isTrue();
    }

    @Test
    void fetchJoinInitialisesLinesAndProducts() {
        List<Order> result = orders.findWithLinesByCustomerEmail("ada@example.com");

        assertThat(result).hasSize(2);                       // distinct: no duplicate orders
        Order first = result.stream().filter(o -> o.getId().equals(paidOrderId)).findFirst().orElseThrow();
        assertThat(isLoaded(first, "lines")).isTrue();
        assertThat(first.getLines()).hasSize(2)
                .allSatisfy(l -> assertThat(isLoaded(l, "product")).isTrue());
    }

    @Test
    void plainFindByIdLeavesLinesLazy() {
        Order o = orders.findById(paidOrderId).orElseThrow();
        assertThat(isLoaded(o, "lines")).isFalse();
    }

    @Test
    void nativeLowStockQuery() {
        List<Product> low = products.findLowStock(5);
        assertThat(low).extracting(Product::getSku).containsExactly("BK-2");
    }

    @Test
    void interfaceProjectionPage() {
        Page<OrderSummary> page = orders.findByStatus(OrderStatus.PAID,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "placedAt")));

        assertThat(page.getTotalElements()).isEqualTo(1);
        OrderSummary s = page.getContent().getFirst();
        assertThat(s.getId()).isEqualTo(paidOrderId);
        assertThat(s.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(s.getCustomer().getName()).isEqualTo("Ada Lovelace");
    }

    /**
     * Why not Hibernate.isInitialized(order.getLines())?  getLines() returns
     * Collections.unmodifiableList(lines): the wrapper is not a Hibernate collection,
     * so isInitialized would always return true. PersistenceUnitUtil checks the field itself.
     */
    private boolean isLoaded(Object entity, String attribute) {
        PersistenceUnitUtil util = em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil();
        return util.isLoaded(entity, attribute);
    }
}
