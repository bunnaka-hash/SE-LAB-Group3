package edu.itc.shop.actuator;

import edu.itc.shop.config.ShopProperties;
import edu.itc.shop.domain.Product;               
import edu.itc.shop.service.ProductService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LowStockHealthIndicator implements HealthIndicator {

    private final ProductService productService;
    private final ShopProperties props;

    public LowStockHealthIndicator(ProductService productService, ShopProperties props) {
        this.productService = productService;
        this.props = props;
    }

    @Override
    public Health health() {
        List<String> low = productService.findAll().stream()     
                .filter(p -> p.getStock() < props.lowStockThreshold())
                .map(Product::getName)
                .toList();

        if (low.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail("lowStockProducts", low).build();
    }
}