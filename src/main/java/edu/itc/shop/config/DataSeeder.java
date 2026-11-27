package edu.itc.shop.config;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Role;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.service.AccountService;

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

    @Bean
    CommandLineRunner seedUsers(UserAccountRepository users, AccountService accounts,
                                @Value("${shop.seed.admin-password}") String adminPassword,
                                @Value("${shop.seed.customer-password}") String customerPassword) {
        return args -> {
            if (!users.existsByUsername("admin")) {
                accounts.register("admin", adminPassword, Set.of(Role.ADMIN, Role.CUSTOMER));
            }
            if (!users.existsByUsername("alice")) {
                accounts.register("alice", customerPassword, Set.of(Role.CUSTOMER));
            }
        };
    }
}
