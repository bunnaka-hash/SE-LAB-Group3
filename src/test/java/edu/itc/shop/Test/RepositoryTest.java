package edu.itc.shop.Test;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.service.ProductService;



import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;


@JdbcTest
@Import(ProductRepository.class)
class ProductRepositoryTest {

    @Autowired
    ProductRepository repository;

    @Test
    void findAllReturnsSeedData() {

        assertEquals(
                5,
                repository.findAll().size()
        );
    }

    @Test
    void findByNameContainingWorks() {

        assertFalse(
                repository
                        .findByNameContaining("top")
                        .isEmpty()
        );
    }
}