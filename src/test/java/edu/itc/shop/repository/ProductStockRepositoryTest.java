package edu.itc.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** The guarded stock update the orders rely on, on the test data (product 1 has 10 units). */
@JdbcTest
@ActiveProfiles("test")
@Import(ProductRepository.class)
class ProductStockRepositoryTest {

    @Autowired
    ProductRepository repository;

    private int stockOf(long id) {
        return repository.findById(id).orElseThrow().stock();
    }

    @Test
    void decrementWithinStockUpdatesOneRow() {
        assertThat(repository.updateStock(1L, -10)).isEqualTo(1);
        assertThat(stockOf(1L)).isZero();
    }

    @Test
    void decrementBeyondStockUpdatesNothing() {
        assertThat(repository.updateStock(1L, -11)).isZero();
        assertThat(stockOf(1L)).isEqualTo(10);
    }

    @Test
    void unknownProductUpdatesNothing() {
        assertThat(repository.updateStock(999L, -1)).isZero();
    }

    @Test
    void productUsedByAnOrderCannotBeDeleted() {
        assertThatThrownBy(() -> repository.delete(1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
