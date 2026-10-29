
package edu.itc.shop.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.CreateProduct;
import edu.itc.shop.web.dto.ProductDto;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDto> list(
            @RequestParam(required = false) String q) {
        return service.search(q).stream()
                .map(ProductDto::from).toList();
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        return ProductDto.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(
            @RequestBody @Valid CreateProduct cmd) {
        ProductDto created = ProductDto.from(service.create(cmd));
        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}/stock")
    public ProductDto updateStock(
            @PathVariable long id,
            @RequestBody StockChange change) {
        return ProductDto.from(
                service.adjustStock(id, change.delta()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record StockChange(int delta) {}
}
