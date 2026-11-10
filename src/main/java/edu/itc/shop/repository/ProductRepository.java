package edu.itc.shop.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import edu.itc.shop.domain.Category;
import edu.itc.shop.domain.Product;
import edu.itc.shop.web.dto.ProductView;

public interface ProductRepository extends JpaRepository<Product, Long>,
                                           JpaSpecificationExecutor<Product> {

    // ---- derived queries ----
    boolean existsBySku(String sku);

    List<Product> findTop3ByCategoryOrderByPriceAmountDesc(Category category);   // price.amount

    List<Product> findByTagsName(String tagName);                                 // joins product_tag + tag

    // ---- record (DTO) projection ----
    // The record field "amount" lives at price.amount in the entity, so a purely derived
    // DTO query cannot map it by name. A constructor expression maps it explicitly.
    @Query(value = """
            select new edu.itc.shop.web.dto.ProductView(p.id, p.sku, p.name, p.price.amount, p.stock)
              from Product p
             where p.category = :category
            """,
           countQuery = "select count(p) from Product p where p.category = :category")
    Page<ProductView> findByCategory(@Param("category") Category category, Pageable pageable);

    // ---- native SQL ----
    @Query(value = "SELECT p.* FROM product p WHERE p.stock < :min ORDER BY p.stock",
           nativeQuery = true)
    List<Product> findLowStock(@Param("min") int min);
}
