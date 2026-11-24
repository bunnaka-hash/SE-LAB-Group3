package edu.itc.shop.config;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedProducts(ProductRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Product("USB-C cable", new BigDecimal("7.90"), 25));
                repo.save(new Product("Keyboard", new BigDecimal("49.00"), 10));
                repo.save(new Product("Monitor", new BigDecimal("189.00"), 4));
            }
        };
    }
}
