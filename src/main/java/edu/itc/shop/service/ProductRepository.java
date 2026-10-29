package edu.itc.shop.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import edu.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Product> findAll() {
        return jdbcClient.sql("""
            SELECT id,name,price,stock
            FROM product
            ORDER BY id
            """)
            .query(Product.class).list();
    }

    public Optional<Product> findById(long id) {
        return jdbcClient.sql("""
                SELECT id,name,price,stock
                FROM product
                WHERE id = ?
                """)
                .param(id)
                .query(Product.class)
                .optional();
    }

    public List<Product> findByNameContaining(String text) {
        return jdbcClient.sql("""
                SELECT id,name,price,stock
                FROM product
                WHERE LOWER(name)
                LIKE LOWER(?)
                """)
                .param("%" + text + "%")
                .query(Product.class)
                .list();
    }

    public long insert(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO product(name,price,stock)
                    VALUES (?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, product.name());
            statement.setBigDecimal(2, product.price());
            statement.setInt(3, product.stock());
            return statement;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Product insert did not return a generated ID");
        }
        return id.longValue();
    }

    public int updateStock(long id, int delta) {

        return jdbcClient.sql("""
                UPDATE product
                SET stock = stock + ?
                WHERE id = ?
                """)
                .param(delta)
                .param(id)
                .update();
    }

    public int delete(long id) {

        return jdbcClient.sql("""
                DELETE FROM product
                WHERE id = ?
                """)
                .param(id)
                .update();
    }
}
