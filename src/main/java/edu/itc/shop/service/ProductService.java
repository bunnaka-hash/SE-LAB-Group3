package edu.itc.shop.service;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Category;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.repository.ProductSpecs;
import edu.itc.shop.web.dto.ProductView;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    /** Specification search; entities are mapped to the record here, never in the controller. */
    @Transactional(readOnly = true)
    public Page<ProductView> search(String q, Category category, BigDecimal maxPrice,
                                    Boolean inStock, Pageable pageable) {
        return products.findAll(ProductSpecs.filter(q, category, maxPrice, inStock), pageable)
                       .map(ProductView::from);
    }
}
