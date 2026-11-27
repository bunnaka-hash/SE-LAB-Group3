package edu.itc.shop.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceOrder(
    @NotNull Long productId,
    @Min(1) int quantity
) {}