package edu.itc.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.itc.shop.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
    long countByName(String name);
}
