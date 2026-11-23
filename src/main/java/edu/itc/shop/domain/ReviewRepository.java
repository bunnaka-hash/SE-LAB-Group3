package edu.itc.shop.domain;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ReviewRepository {

    private final AtomicLong nextId = new AtomicLong(1);

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Review>>
            reviews = new ConcurrentHashMap<>();

    public List<Review> findByProductId(long productId) {
        return List.copyOf(
                reviews.getOrDefault(
                        productId,
                        new CopyOnWriteArrayList<>()
                )
        );
    }

    public Review save(
            long productId,
            int stars,
            String author,
            String text) {

        Review review = new Review(
                nextId.getAndIncrement(),
                productId,
                stars,
                author,
                text
        );

        reviews.computeIfAbsent(
                productId,
                id -> new CopyOnWriteArrayList<>()
        ).add(review);

        return review;
    }
}