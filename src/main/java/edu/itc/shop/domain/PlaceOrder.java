package edu.itc.shop.domain;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

public record PlaceOrder(
        @NotNull @NotEmpty List<@Valid @NotNull Line> lines) {
}
