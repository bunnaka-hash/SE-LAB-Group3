package edu.itc.shop.domain;

import java.util.List;
import jakarta.ws.rs.NotFoundException;

/** Business rules (unique SKU, non-negative stock) live here, not in the resource. */
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) { this.repo = repo; }

    public List<Product> findAll() {
        return repo.findAll();
    }

    public Product find(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    // synchronized: check-then-save must not interleave with another request
    public synchronized Product create(Product p) {
        repo.findBySku(p.sku()).ifPresent(existing -> { throw new DuplicateSkuException(p.sku()); });
        return repo.save(new Product(0, p.name(), p.sku(), p.price(), p.stock(), 0));
    }

    public synchronized Product replace(long id, Product p) {
        Product current = find(id);
        repo.findBySku(p.sku())
                .filter(other -> other.id() != id)
                .ifPresent(other -> { throw new DuplicateSkuException(p.sku()); });
        return repo.save(new Product(id, p.name(), p.sku(), p.price(), p.stock(), current.version()));
    }

    public synchronized Product adjustStock(long id, int delta) {
        Product p = find(id);
        if ((long) p.stock() + delta < 0) {
            throw new InsufficientStockException(id, p.stock(), delta);
        }
        return repo.save(new Product(id, p.name(), p.sku(), p.price(), p.stock() + delta, p.version()));
    }

    public synchronized void delete(long id) {
        if (!repo.delete(id)) {
            throw new NotFoundException("Product " + id + " not found");
        }
    }
}
