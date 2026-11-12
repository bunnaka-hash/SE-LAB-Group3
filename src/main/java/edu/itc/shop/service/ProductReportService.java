package edu.itc.shop.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Category;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.ProductReportRow;

@Service
public class ProductReportService {
    private final ProductRepository products;

    public ProductReportService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductReportRow> products(String q, Category category, BigDecimal maxPrice,
                                           Integer minStock, String tag, String sort) {
        return products.findProductReport(q, category, maxPrice, minStock, tag, sort);
    }
}
