package edu.itc.shop.domain;

public record Review(
        long id,
        long productId,
        int stars,
        String author,
        String text
) {}