package edu.itc.shop.web;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import edu.itc.shop.domain.Category;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.ProductView;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/products?q=key&category=HARDWARE&maxPrice=60&inStock=true&page=0&size=10
    // Empty values (q=&category=) arrive as null and simply switch that filter off.
    @GetMapping
    public Page<ProductView> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Category category,
                                    @RequestParam(required = false) BigDecimal maxPrice,
                                    @RequestParam(required = false) Boolean inStock,
                                    @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return productService.search(q, category, maxPrice, inStock, pageable);
    }
}
