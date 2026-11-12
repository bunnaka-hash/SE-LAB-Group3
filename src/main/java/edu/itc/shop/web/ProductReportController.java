package edu.itc.shop.web;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import edu.itc.shop.domain.Category;
import edu.itc.shop.service.ProductReportService;
import edu.itc.shop.web.dto.ProductReportRow;

@RestController
@RequestMapping("/api/reports")
public class ProductReportController {
    private final ProductReportService reports;

    public ProductReportController(ProductReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/products")
    public List<ProductReportRow> products(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Category category,
                                           @RequestParam(required = false) BigDecimal maxPrice,
                                           @RequestParam(required = false) Integer minStock,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) String sort) {
        return reports.products(q, category, maxPrice, minStock, tag, sort);
    }
}
