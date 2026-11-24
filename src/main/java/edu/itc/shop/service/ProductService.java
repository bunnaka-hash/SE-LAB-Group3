package edu.itc.shop.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.ProductRepository;

@Service
@Transactional
public class ProductService {
    private final ProductRepository repo;

    public ProductService(ProductRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Product find(long id) {
        return repo.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product create(Product product) {
        return repo.save(product);
    }

    public void delete(long id) {
        if (!repo.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        repo.deleteById(id);
    }
}
