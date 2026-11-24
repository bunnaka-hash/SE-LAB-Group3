package edu.itc.shop.web.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import edu.itc.shop.domain.Product;

public record ProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @PositiveOrZero int stock) {

    public Product toProduct() {
        return new Product(name.strip(), price, stock);
    }
}
