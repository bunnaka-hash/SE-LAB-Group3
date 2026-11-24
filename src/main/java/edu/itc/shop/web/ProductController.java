package edu.itc.shop.web;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import edu.itc.shop.domain.Product;
import edu.itc.shop.service.ProductService;
import edu.itc.shop.web.dto.ProductRequest;
import edu.itc.shop.web.dto.ProductResponse;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    public List<ProductResponse> list() {
        return service.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable("id") long id) {
        return ProductResponse.from(service.find(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request,
                                                  UriComponentsBuilder uri) {
        Product saved = service.create(request.toProduct());
        URI location = uri.path("/api/products/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(ProductResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
