
package edu.itc.shop.web.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProduct(
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @PositiveOrZero int stock
) {}
