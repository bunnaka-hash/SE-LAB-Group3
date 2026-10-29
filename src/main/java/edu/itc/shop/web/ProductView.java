package edu.itc.shop.web;

import edu.itc.shop.domain.Product;  

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ProductView {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final BigDecimal gross;
    private final int stock;

    private ProductView(Long id, String name, BigDecimal price, BigDecimal gross, int stock) {
        this.id = id; this.name = name; this.price = price; this.gross = gross; this.stock = stock;
    }

    public static ProductView from(Product p, BigDecimal taxRate) {
        BigDecimal gross = p.getPrice()
                .multiply(BigDecimal.ONE.add(taxRate))
                .setScale(2, RoundingMode.HALF_UP);
        return new ProductView(p.getId(), p.getName(), p.getPrice(), gross, p.getStock());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getGross() { return gross; }
    public int getStock() { return stock; }
}