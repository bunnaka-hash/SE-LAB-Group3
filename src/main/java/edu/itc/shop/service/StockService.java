package edu.itc.shop.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.Product;
import edu.itc.shop.repository.OutOfStockException;
import edu.itc.shop.repository.ProductRepository;

@Service
public class StockService {
    private static final int MAX_OPTIMISTIC_ATTEMPTS = 100;

    private final ProductRepository products;
    private final TransactionTemplate transactions;

    public StockService(ProductRepository products, PlatformTransactionManager transactionManager) {
        this.products = products;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void reserve(Long productId, int qty) {
        validateQuantity(qty);
        Product product = products.findForUpdate(productId).orElseThrow(
                () -> new IllegalArgumentException("Product " + productId + " not found"));
        ensureAvailable(product, productId, qty);
        product.setStock(product.getStock() - qty);
    }

    /** Returns the number of retries; each retry gets a fresh transaction and entity version. */
    public int reserveOptimistically(Long productId, int qty) {
        validateQuantity(qty);
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            try {
                transactions.executeWithoutResult(status -> {
                    Product product = products.findById(productId).orElseThrow(
                            () -> new IllegalArgumentException("Product " + productId + " not found"));
                    ensureAvailable(product, productId, qty);
                    product.setStock(product.getStock() - qty);
                });
                return attempt - 1;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_OPTIMISTIC_ATTEMPTS) {
                    throw e;
                }
                Thread.yield();
            }
        }
        throw new IllegalStateException("Optimistic reservation retry loop exhausted");
    }

    @Transactional
    public void reserveAtomically(Long productId, int qty) {
        validateQuantity(qty);
        if (products.decrementStockIfAvailable(productId, qty) == 0) {
            if (!products.existsById(productId)) {
                throw new IllegalArgumentException("Product " + productId + " not found");
            }
            throw new OutOfStockException(productId, qty);
        }
    }

    private static void ensureAvailable(Product product, Long productId, int qty) {
        if (product.getStock() < qty) {
            throw new OutOfStockException(productId, qty);
        }
    }

    private static void validateQuantity(int qty) {
        if (qty < 1) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
    }
}
