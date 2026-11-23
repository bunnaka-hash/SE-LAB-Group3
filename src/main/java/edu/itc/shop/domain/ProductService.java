
package edu.itc.shop.domain;

import java.util.Optional;
import java.util.List;
import jakarta.inject.Inject;

public class ProductService {

    private final ProductRepository repo;

    @Inject
    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<Product> findAll() {
        return repo.findAll();
    }

    public Optional<Product> findById(long id) {
        return repo.findById(id);
    }

    public Product create(Product product) {
        if (repo.findBySku(product.sku()).isPresent()) {
            throw new DuplicateSkuException(product.sku());
        }

        return repo.save(product);
    }

    public Optional<Product> update(long id, Product replacement) {
        Optional<Product> existing = repo.findById(id);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Optional<Product> duplicate = repo.findBySku(replacement.sku());

        if (duplicate.isPresent() && duplicate.get().id() != id) {
            throw new DuplicateSkuException(replacement.sku());
        }

        Product updated = new Product(
                id,
                replacement.name(),
                replacement.sku(),
                replacement.price(),
                replacement.stock(),
                existing.get().version()
        );

        return Optional.of(repo.save(updated));
    }

    public Optional<Product> changeStock(long id, int delta) {
        Optional<Product> existing = repo.findById(id);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Product current = existing.get();
        int newStock = current.stock() + delta;

        if (newStock < 0) {
            throw new InsufficientStockException();
        }

        Product updated = new Product(
                current.id(),
                current.name(),
                current.sku(),
                current.price(),
                newStock,
                current.version()
        );

        return Optional.of(repo.save(updated));
    }

    public boolean delete(long id) {
        return repo.delete(id);
    }
}
