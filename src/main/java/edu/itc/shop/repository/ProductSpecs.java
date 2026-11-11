package edu.itc.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.Product;

public final class ProductSpecs {
    private ProductSpecs() { }

    public static Specification<Product> nameContains(String q) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.<String>get("name")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Product> inCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Product> cheaperThan(BigDecimal max) {
        return (root, query, cb) ->
                cb.le(root.<Money>get("price").<BigDecimal>get("amount"), max);
    }

    public static Specification<Product> inStock() {
        return (root, query, cb) -> cb.greaterThan(root.<Integer>get("stock"), 0);
    }

    /**
     * Combines only the filters whose parameter is present.
     * inStock=false or null means "no stock filter".
     * Nulls are removed before allOf, so this works whether or not
     * the Spring Data version tolerates null specifications.
     */
    public static Specification<Product> filter(String q, Category category,
                                                BigDecimal maxPrice, Boolean inStock) {
        List<Specification<Product>> specs = Stream.of(
                        q == null || q.isBlank() ? null : nameContains(q.trim()),
                        category == null ? null : inCategory(category),
                        maxPrice == null ? null : cheaperThan(maxPrice),
                        Boolean.TRUE.equals(inStock) ? inStock() : null)
                .filter(Objects::nonNull)
                .toList();
        return Specification.allOf(specs);   // empty list = no restriction
    }
}
