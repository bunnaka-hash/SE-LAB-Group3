package edu.itc.shop.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import edu.itc.shop.domain.Tag;

/** Used by DataSeeder now and by FlushOrderTest in Task 4. */
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);
}
