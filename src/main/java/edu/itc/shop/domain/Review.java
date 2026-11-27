package edu.itc.shop.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Task 5: product review. The text is stored exactly as typed; escaping is done on output. */
@Entity
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Review() { }   // for JPA

    public Review(Long productId, String author, int stars, String text) {
        this.productId = productId;
        this.author = author;
        this.stars = stars;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getAuthor() { return author; }
    public int getStars() { return stars; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
