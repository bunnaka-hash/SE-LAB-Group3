package edu.itc.shop.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("shop")
public record ShopProperties(
        @NotBlank String currency,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
        @PositiveOrZero int lowStockThreshold) {
}