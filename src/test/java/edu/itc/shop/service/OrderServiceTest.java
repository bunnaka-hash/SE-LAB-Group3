package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired TestData data;
    @Autowired EntityManagerFactory emf;

    Statistics stats;

    @BeforeEach
    void setUp() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    // (a) status is PAID on reload, (b) exactly one UPDATE, and there is no save() in markPaid
    @Test
    void markPaidIssuesExactlyOneUpdateWithoutSave() {
        Long id = data.newOrder();

        stats.clear();
        orderService.markPaid(id);

        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
    }

    // Detached entity: changes are ignored until save() (= merge: SELECT + UPDATE)
    @Test
    void detachedChangeIsLostUntilSaveMerges() {
        Long id = data.newProductId(5);

        // No transaction here and open-in-view is off, so p is detached right away.
        Product p = products.findById(id).orElseThrow();
        p.setStock(99);

        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(5); // unchanged

        stats.clear();
        products.save(p); // id != null -> em.merge(): one SELECT, then one UPDATE

        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(99);
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
        // Detached merge select + versioned update + reload after the repository transaction ends.
    }

    // The read-only trap, kept as documentation
    @Test
    void modificationInsideReadOnlyTransactionIsNeverFlushed() {
        Long id = data.newOrder();

        stats.clear();
        orderService.wrongReadOnlyChange(id, OrderStatus.CANCELLED);

        assertThat(stats.getEntityUpdateCount()).isZero();
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void viewReturnsDtoWithLines() {
        Long id = data.newOrder();
        assertThat(orderService.view(id).lines()).hasSize(1);
    }
}