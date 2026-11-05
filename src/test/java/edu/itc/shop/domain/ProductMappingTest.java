package edu.itc.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import jakarta.persistence.EntityManager;

/**
 * Runs against the real H2 datasource (Replace.NONE) so that Flyway applies
 * V1__init.sql and Hibernate validates the entities against it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductMappingTest {

	@Autowired
	TestEntityManager tem;

	@Autowired
	EntityManager em;

	@Test
	void productRoundTripsAndEnumIsStoredAsText() {
		Product saved = tem.persistAndFlush(
				new Product("HW-001", "Mechanical keyboard", Money.eur("89.90"), Category.HARDWARE, 10));
		Long id = saved.getId();
		tem.clear();

		Product loaded = tem.find(Product.class, id);

		assertThat(loaded).isNotSameAs(saved);
		assertThat(loaded.getPrice().amount()).isEqualByComparingTo("89.90");
		assertThat(loaded.getPrice().currency()).isEqualTo("EUR");
		assertThat(loaded.getCategory()).isEqualTo(Category.HARDWARE);

		// the enum must be stored as its name, not as an ordinal number
		Object raw = em.createNativeQuery("select category from product where id = :id")
				.setParameter("id", id)
				.getSingleResult();
		assertThat(raw.toString()).contains("HARDWARE");
	}
}
