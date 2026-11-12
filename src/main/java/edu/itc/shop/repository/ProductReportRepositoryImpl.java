package edu.itc.shop.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Money_;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Product_;
import edu.itc.shop.domain.Tag;
import edu.itc.shop.domain.Tag_;
import edu.itc.shop.service.InvalidReportSortException;
import edu.itc.shop.web.dto.ProductReportRow;

@Repository
public class ProductReportRepositoryImpl implements ProductReportRepository {

    private static final Map<String, String> SORTABLE = Map.of(
            "sku", "sku", "name", "name", "amount", "amount", "stock", "stock", "tagCount", "tagCount");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ProductReportRow> findProductReport(String q, Category category, BigDecimal maxPrice,
                                                    Integer minStock, String tag, String sort) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductReportRow> query = cb.createQuery(ProductReportRow.class);
        Root<Product> product = query.from(Product.class);
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(product.get(Product_.sku)), pattern),
                    cb.like(cb.lower(product.get(Product_.name)), pattern)));
        }
        if (category != null) {
            predicates.add(cb.equal(product.get(Product_.category), category));
        }
        if (maxPrice != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get(Product_.price).get(Money_.amount), maxPrice));
        }
        if (minStock != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get(Product_.stock), minStock));
        }
        if (tag != null && !tag.isBlank()) {
            Join<Product, Tag> tags = product.join(Product_.tags, JoinType.INNER);
            predicates.add(cb.equal(cb.lower(tags.get(Tag_.name)), tag.toLowerCase(Locale.ROOT)));
        }

        Subquery<Long> tagCount = query.subquery(Long.class);
        Root<Product> correlatedProduct = tagCount.correlate(product);
        Join<Product, Tag> countedTags = correlatedProduct.join(Product_.tags, JoinType.LEFT);
        tagCount.select(cb.countDistinct(countedTags.get(Tag_.id)));

        query.select(cb.construct(ProductReportRow.class,
                        product.get(Product_.sku),
                        product.get(Product_.name),
                        product.get(Product_.price).get(Money_.amount),
                        product.get(Product_.stock),
                        tagCount))
                .where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new))
                .groupBy(product.get(Product_.sku),
                        product.get(Product_.name),
                        product.get(Product_.price).get(Money_.amount),
                        product.get(Product_.stock));

        SortSpec sortSpec = parseSort(sort);
        query.orderBy(sortSpec.direction().isAscending()
                ? cb.asc(sortExpression(sortSpec.property(), product, tagCount))
                : cb.desc(sortExpression(sortSpec.property(), product, tagCount)));

        TypedQuery<ProductReportRow> result = entityManager.createQuery(query);
        return result.getResultList();
    }

    private static SortSpec parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return new SortSpec("sku", Sort.Direction.ASC);
        }
        String[] parts = sort.split(",", -1);
        String property = SORTABLE.get(parts[0]);
        if (property == null || parts.length > 2) {
            throw new InvalidReportSortException(sort);
        }
        Sort.Direction direction;
        try {
            direction = parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidReportSortException(sort);
        }
        return new SortSpec(property, direction);
    }

    private static jakarta.persistence.criteria.Expression<?> sortExpression(
            String property, Root<Product> product, Subquery<Long> tagCount) {
        return switch (property) {
            case "sku" -> product.get(Product_.sku);
            case "name" -> product.get(Product_.name);
            case "amount" -> product.get(Product_.price).get(Money_.amount);
            case "stock" -> product.get(Product_.stock);
            case "tagCount" -> tagCount;
            default -> throw new IllegalStateException("Unreachable sort property: " + property);
        };
    }

    private record SortSpec(String property, Sort.Direction direction) { }
}
