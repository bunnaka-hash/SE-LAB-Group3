package edu.itc.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.itc.shop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
