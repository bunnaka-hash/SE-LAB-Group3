package edu.itc.shop.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import edu.itc.shop.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);
}
