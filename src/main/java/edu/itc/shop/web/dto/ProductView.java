package edu.itc.shop.web.dto;

import java.math.BigDecimal;
import edu.itc.shop.domain.Product;

/** Record projection / DTO for products. */
public record ProductView(Long id, String sku, String name, BigDecimal amount, int stock) {

    /** Used by the service when mapping Specification results (entities) to the DTO. */
    public static ProductView from(Product p) {
        return new ProductView(p.getId(), p.getSku(), p.getName(), p.getPrice().amount(), p.getStock());
    }
}
