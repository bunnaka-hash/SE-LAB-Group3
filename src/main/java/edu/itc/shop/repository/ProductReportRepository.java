package edu.itc.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import edu.itc.shop.domain.Category;
import edu.itc.shop.web.dto.ProductReportRow;

public interface ProductReportRepository {
    List<ProductReportRow> findProductReport(String q, Category category, BigDecimal maxPrice,
                                             Integer minStock, String tag, String sort);
}
