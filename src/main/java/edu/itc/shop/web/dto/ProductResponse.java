package edu.itc.shop.web.dto;

import java.math.BigDecimal;
import edu.itc.shop.domain.Product;

public record ProductResponse(Long id, String name, BigDecimal price, int stock) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getStock());
    }
}
