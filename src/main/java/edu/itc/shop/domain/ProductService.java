package edu.itc.shop.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * Business rules (unique SKU, non-negative stock) live here, not in the
 * resource.
 */
public class ProductService {

    /** Whitelist: only these fields can ever be sorted on. */
    private static final Map<String, Comparator<Product>> SORTS = Map.of(
            "name", Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER),
            "price", Comparator.comparing(Product::price),
            "stock", Comparator.comparingInt(Product::stock));

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    public List<Product> findAll() {
        return repo.findAll();
    }

    // ---- NEW in Task 3 ----
    public List<Product> search(String q, Money min, Money max, boolean inStock,
            String sortField, boolean descending) {
        Comparator<Product> cmp = SORTS.get(sortField);
        if (cmp == null) {
            throw new BadRequestException("Cannot sort by '" + sortField + "'; allowed: name, price, stock");
        }
        requireEur(min, "minPrice");
        requireEur(max, "maxPrice");
        if (descending)
            cmp = cmp.reversed();

        String needle = (q == null || q.isBlank()) ? null : q.toLowerCase(Locale.ROOT);
        return repo.findAll().stream()
                .filter(p -> needle == null || p.name().toLowerCase(Locale.ROOT).contains(needle))
                .filter(p -> min == null || p.price().compareTo(min.amount()) >= 0)
                .filter(p -> max == null || p.price().compareTo(max.amount()) <= 0)
                .filter(p -> !inStock || p.stock() > 0)
                .sorted(cmp)
                .toList();
    }

    private static void requireEur(Money m, String name) {
        if (m != null && !"EUR".equals(m.currency())) {
            throw new BadRequestException(name + " must be in EUR, got " + m.currency());
        }
    }
    // ---- end of new code ----

    public Product find(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    // synchronized: check-then-save must not interleave with another request
    public synchronized Product create(Product p) {
        repo.findBySku(p.sku()).ifPresent(existing -> {
            throw new DuplicateSkuException(p.sku());
        });
        return repo.save(new Product(0, p.name(), p.sku(), p.price(), p.stock(), 0));
    }

    public synchronized Product replace(long id, Product p) {
        Product current = find(id);
        repo.findBySku(p.sku())
                .filter(other -> other.id() != id)
                .ifPresent(other -> {
                    throw new DuplicateSkuException(p.sku());
                });
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
