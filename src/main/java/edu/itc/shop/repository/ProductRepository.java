
package edu.itc.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import edu.itc.shop.domain.Product;

@Repository
public class ProductRepository {

    private final JdbcClient jdbc;

    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Product> findAll() {
        return jdbc.sql(
                "SELECT id, name, price, stock FROM product ORDER BY id")
                .query(Product.class).list();
    }

    public Optional<Product> findById(long id) {
        return jdbc.sql("""
                SELECT id, name, price, stock
                FROM product WHERE id = :id
                """)
                .param("id", id)
                .query(Product.class)
                .optional();
    }

    public List<Product> findByNameContaining(String name) {
        return jdbc.sql("""
                SELECT id, name, price, stock
                FROM product
                WHERE LOWER(name) LIKE LOWER(:name)
                ORDER BY id
                """)
                .param("name", "%" + name + "%")
                .query(Product.class)
                .list();
    }

    public long insert(Product p) {
        var keys = new GeneratedKeyHolder();

        jdbc.sql("""
                INSERT INTO product(name, price, stock)
                VALUES (:name, :price, :stock)
                """)
                .param("name", p.name())
                .param("price", p.price())
                .param("stock", p.stock())
                .update(keys);

        if (keys.getKey() == null) {
            throw new IllegalStateException("No generated product ID returned");
        }
        return keys.getKey().longValue();
    }

    public int updateStock(long id, int delta) {
        return jdbc.sql("""
                UPDATE product
                SET stock = stock + :delta
                WHERE id = :id AND stock + :delta >= 0
                """)
                .param("id", id)
                .param("delta", delta)
                .update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM product WHERE id = :id")
                .param("id", id)
                .update();
    }
}
