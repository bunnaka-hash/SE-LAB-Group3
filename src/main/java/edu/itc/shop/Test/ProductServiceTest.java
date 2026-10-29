package edu.itc.shop.Test;

import com.example.demo.domain.Product;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    ProductRepository repository;

    @Autowired
    ProductService service;

    @Test
    void rollbackWhenQuantityTooLarge() {

        Product source =
                repository.findById(1L).orElseThrow();

        Product target =
                repository.findById(2L).orElseThrow();

        int sourceStock = source.stock();
        int targetStock = target.stock();

        assertThrows(
                Exception.class,
                () -> service.restock(
                        1L,
                        2L,
                        10_000
                )
        );

        Product sourceAfter =
                repository.findById(1L).orElseThrow();

        Product targetAfter =
                repository.findById(2L).orElseThrow();

        assertEquals(
                sourceStock,
                sourceAfter.stock());

        assertEquals(
                targetStock,
                targetAfter.stock());
    }
}