package edu.itc.shop.web.dto;

import edu.itc.shop.domain.Review;

public record ReviewResponse(Long id, String author, int stars, String text) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(), r.getAuthor(), r.getStars(), r.getText());
    }
}
