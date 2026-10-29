package edu.itc.shop.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
@Import(CustomerRepository.class)
class CustomerRepositoryTest {

    @Autowired
    CustomerRepository repository;

    @Test
    void findAllReturnsTestCustomersInIdOrder() {
        assertThat(repository.findAll()).extracting(Customer::email)
                .containsExactly("sokha@example.com", "dara@example.com", "vanna@example.com");
    }

    @Test
    void findByIdReturnsCustomerOrEmpty() {
        assertThat(repository.findById(2L)).contains(new Customer(2L, "Dara Kim", "dara@example.com"));
        assertThat(repository.findById(999L)).isEmpty();
    }
}
