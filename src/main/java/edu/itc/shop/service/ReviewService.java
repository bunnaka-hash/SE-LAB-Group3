package edu.itc.shop.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Review;
import edu.itc.shop.repository.ReviewRepository;

@Service
@Transactional
public class ReviewService {
    private final ReviewRepository reviews;
    private final ProductService products;

    public ReviewService(ReviewRepository reviews, ProductService products) {
        this.reviews = reviews;
        this.products = products;
    }

    public Review add(Long productId, String author, int stars, String text) {
        products.find(productId);   // 404 if the product does not exist
        return reviews.save(new Review(productId, author, stars, text));
    }

    @Transactional(readOnly = true)
    public List<Review> forProduct(long productId) {
        products.find(productId);
        return reviews.findByProductIdOrderByIdAsc(productId);
    }
}
