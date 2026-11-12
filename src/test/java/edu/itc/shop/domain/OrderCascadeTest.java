package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OrderCascadeTest {
    @Autowired TestEntityManager testEntityManager;
    @Autowired EntityManager entityManager;

    @Test
    void persistsLinesWithoutUpdatesThenOrphanRemovalAndCascadeRemoveWork() {
        long initialLineCount = lineCount();
        Customer customer = testEntityManager.persist(new Customer(
                "Cascade Customer", "cascade-" + UUID.randomUUID() + "@example.com",
                new Address("1 Test Road", "London", "N1"), new Address("1 Test Road", "London", "N1")));
        Product keyboard = testEntityManager.persist(new Product(
                "OC-K-" + UUID.randomUUID().toString().substring(0, 8),
                "Keyboard", Money.eur("20.00"), Category.HARDWARE, 5));
        Product mouse = testEntityManager.persist(new Product(
                "OC-M-" + UUID.randomUUID().toString().substring(0, 8),
                "Mouse", Money.eur("5.00"), Category.HARDWARE, 8));
        testEntityManager.flush();

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Order order = new Order(customer);
        order.addLine(keyboard, 2);
        order.addLine(mouse, 1);
        entityManager.persist(order);
        entityManager.flush();

        assertThat(statistics.getEntityInsertCount()).isEqualTo(3);
        assertThat(statistics.getEntityUpdateCount()).isZero();
        Long orderId = order.getId();
        entityManager.clear();

        Order loaded = entityManager.find(Order.class, orderId);
        assertThat(loaded.getLines()).hasSize(2);
        assertThat(loaded.total().amount()).isEqualByComparingTo("45.00");

        OrderLine removedLine = loaded.getLines().getFirst();
        loaded.removeLine(removedLine);
        entityManager.flush();
        assertThat(lineCount()).isEqualTo(initialLineCount + 1);
        assertThat(statistics.getEntityDeleteCount()).isEqualTo(1);

        entityManager.remove(loaded);
        entityManager.flush();
        assertThat(lineCount()).isEqualTo(initialLineCount);
        assertThat(statistics.getEntityDeleteCount()).isEqualTo(3);
    }

    private long lineCount() {
        return entityManager.createQuery("select count(l) from OrderLine l", Long.class)
                .getSingleResult();
    }
}
