package edu.itc.shop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import edu.itc.shop.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByIdAsc(Long productId);
}
