package edu.itc.shop.web;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import edu.itc.shop.domain.Review;
import edu.itc.shop.service.ReviewService;
import edu.itc.shop.web.dto.ReviewRequest;
import edu.itc.shop.web.dto.ReviewResponse;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) { this.service = service; }

    /** Authenticated: the author is the token subject, never taken from the body. */
    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody ReviewRequest req,
                                                 Authentication auth) {
        Review saved = service.add(req.productId(), auth.getName(), req.stars(), req.text());
        URI location = URI.create("/api/products/" + saved.getProductId() + "/reviews");
        return ResponseEntity.created(location).body(ReviewResponse.from(saved));
    }

    /** Public (covered by GET /api/products/** permitAll). */
    @GetMapping("/products/{id}/reviews")
    public List<ReviewResponse> list(@PathVariable("id") long id) {
        return service.forProduct(id).stream().map(ReviewResponse::from).toList();
    }
}
