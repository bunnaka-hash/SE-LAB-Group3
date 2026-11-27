package edu.itc.shop.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotNull Long productId,
                            @Min(1) @Max(5) int stars,
                            @NotBlank @Size(max = 1000) String text) {}
