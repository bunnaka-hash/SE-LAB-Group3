package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class EntityIdentityTest {
    @Autowired TestEntityManager testEntityManager;
    @Autowired EntityManager entityManager;

    @Test
    void hashSetMembershipSurvivesPersistenceAndAllEntitiesCompareByPersistentIdentity() {
        Customer customer = new Customer("Identity Customer", "identity-" + System.nanoTime() + "@example.com",
                new Address("2 Test Road", "London", "N2"), new Address("2 Test Road", "London", "N2"));
        Product product = new Product("ID-" + System.nanoTime(), "Identity product",
                Money.eur("12.50"), Category.TOYS, 3);
        Tag tag = new Tag("identity-" + System.nanoTime());
        product.tag(tag);
        Order order = new Order(customer);
        OrderLine line = order.addLine(product, 2);
        Set<Product> products = new HashSet<>();
        products.add(product);

        entityManager.persist(customer);
        entityManager.persist(tag);
        entityManager.persist(product);
        entityManager.persist(order);
        entityManager.flush();
        assertThat(products).contains(product);
        assertThat(tag.getProducts()).contains(product);

        Long customerId = customer.getId();
        Long productId = product.getId();
        Long tagId = tag.getId();
        Long orderId = order.getId();
        Long lineId = line.getId();
        testEntityManager.clear();

        Customer loadedCustomer = entityManager.find(Customer.class, customerId);
        Product loadedProduct = entityManager.find(Product.class, productId);
        Tag loadedTag = entityManager.find(Tag.class, tagId);
        Order loadedOrder = entityManager.find(Order.class, orderId);
        OrderLine loadedLine = entityManager.find(OrderLine.class, lineId);

        assertEqualButDistinct(customer, loadedCustomer);
        assertEqualButDistinct(product, loadedProduct);
        assertEqualButDistinct(tag, loadedTag);
        assertEqualButDistinct(order, loadedOrder);
        assertEqualButDistinct(line, loadedLine);
        assertThat(products).contains(loadedProduct);
    }

    private static void assertEqualButDistinct(Object original, Object reloaded) {
        assertThat(reloaded).isEqualTo(original).isNotSameAs(original);
        assertThat(reloaded.hashCode()).isEqualTo(original.hashCode());
    }
}
