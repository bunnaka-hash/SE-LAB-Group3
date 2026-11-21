package edu.itc.shop.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of PATCH /products/{id}/stock, e.g. {"delta": -5}. JSON only. */
public record StockChange(@Min(-1000) @Max(1000) int delta) { }