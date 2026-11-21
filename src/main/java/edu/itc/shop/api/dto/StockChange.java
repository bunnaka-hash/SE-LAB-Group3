package edu.itc.shop.api.dto;

/** Body of PATCH /products/{id}/stock, e.g. {"delta": -5}. JSON only. */
public record StockChange(int delta) {
}
