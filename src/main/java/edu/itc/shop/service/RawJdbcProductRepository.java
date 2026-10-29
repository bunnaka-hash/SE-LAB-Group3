package edu.itc.shop.service;

import org.springframework.stereotype.Repository;
import edu.Product;

import java.util.List;
import java.util.ArrayList;
import javax.sql.DataSource;
import java.sql.*;
import java.math.BigDecimal;

@Repository
public class RawJdbcProductRepository {
    private final DataSource dataSource;
    public RawJdbcProductRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Product> findAll() {

        String sql = "SELECT id, name, price, stock FROM product";
        List<Product> products = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Long id = rs.getLong("id");
                String name = rs.getString("name");
                BigDecimal price = rs.getBigDecimal("price");
                int stock = rs.getInt("stock");
                products.add(new Product(id, name, price, stock));
            }
            return products;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
