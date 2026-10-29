
package edu.itc.shop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.web.dto.CreateProduct;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> search(String name) {
        if (name == null || name.isBlank()) {
            return repository.findAll();
        }

        return repository.findByNameContaining(name.trim());
    }

    public Product findById(long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(CreateProduct request) {
        Product product = new Product(
                null,
                request.name(),
                request.price(),
                request.stock()
        );

        long id = repository.insert(product);

        return findById(id);
    }

    @Transactional
    public Product adjustStock(long id, int delta) {
        int updated = repository.updateStock(id, delta);

        if (updated == 0) {
            if (repository.findById(id).isEmpty()) {
                throw new ProductNotFoundException(id);
            }

            throw new InsufficientStockException(
                    "Stock adjustment would result in negative stock");
        }

        return findById(id);
    }

    @Transactional
    public void delete(long id) {
        int deleted = repository.delete(id);

        if (deleted == 0) {
            throw new ProductNotFoundException(id);
        }
    }

    @Transactional
    public void restock(long fromId, long toId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        int first = repository.updateStock(fromId, -qty);

        if (first == 0) {
            if (repository.findById(fromId).isEmpty()) {
                throw new ProductNotFoundException(fromId);
            }

            throw new InsufficientStockException(
                    "Not enough stock in source product");
        }

        int second = repository.updateStock(toId, qty);

        if (second == 0) {
            throw new ProductNotFoundException(toId);
        }
    }
}
