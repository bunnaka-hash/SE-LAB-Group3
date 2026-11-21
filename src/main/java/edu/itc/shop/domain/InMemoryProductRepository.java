package edu.itc.shop.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe in-memory repository; one instance is shared by all request threads. */
public class InMemoryProductRepository implements ProductRepository {
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public InMemoryProductRepository() {
        save(new Product(0, "Kettle", "KT-01", new BigDecimal("24.90"), 12, 0));
        save(new Product(0, "Mug", "MG-01", new BigDecimal("6.50"), 40, 0));
        save(new Product(0, "Tea", "TE-01", new BigDecimal("4.20"), 0, 0));
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream().sorted(Comparator.comparingLong(Product::id)).toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return store.values().stream().filter(p -> p.sku().equals(sku)).findFirst();
    }

    @Override
    public Product save(Product p) {
        long id = p.id() == 0 ? ids.incrementAndGet() : p.id();
        // atomic read-modify-write so the version increment is not lost under concurrency
        return store.compute(id, (k, existing) -> new Product(id, p.name(), p.sku(), p.price(), p.stock(),
                existing == null ? 1 : existing.version() + 1));
    }

    @Override
    public boolean delete(long id) {
        return store.remove(id) != null;
    }
}
