package edu.itc.shop.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/orders}. "No product twice" is checked by {@link OrderService}. */
public record PlaceOrder(
        @NotNull Long customerId,
        @NotEmpty @Valid List<@NotNull Line> lines
) {
    public record Line(@NotNull Long productId, @Min(1) int qty) {}
}
