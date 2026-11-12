package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import edu.itc.shop.repository.OutOfStockException;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class StockReservationTest {
    private static final Logger log = LoggerFactory.getLogger(StockReservationTest.class);
    private static final int RESERVERS = 10;

    @Autowired StockService stock;
    @Autowired ProductRepository products;
    @Autowired TestData data;

    @Test
    void pessimisticOptimisticAndAtomicReservationsNeverOversell() throws Exception {
        Long pessimisticProduct = data.newProductId(5);
        RunResult pessimistic = runConcurrent("pessimistic", () -> {
            stock.reserve(pessimisticProduct, 1);
            return 0;
        });
        assertResult(pessimisticProduct, pessimistic, false);

        Long optimisticProduct = data.newProductId(5);
        RunResult optimistic = runConcurrent("optimistic",
                () -> stock.reserveOptimistically(optimisticProduct, 1));
        assertResult(optimisticProduct, optimistic, true);

        Long atomicProduct = data.newProductId(5);
        RunResult atomic = runConcurrent("atomic update", () -> {
            stock.reserveAtomically(atomicProduct, 1);
            return 0;
        });
        assertResult(atomicProduct, atomic, false);
    }

    private RunResult runConcurrent(String name, Reservation reservation) throws Exception {
        CountDownLatch ready = new CountDownLatch(RESERVERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger retries = new AtomicInteger();
        long startNanos = System.nanoTime();
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(RESERVERS)) {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int i = 0; i < RESERVERS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return new IllegalStateException("Reservation start timed out");
                    }
                    try {
                        retries.addAndGet(reservation.reserve());
                        return null;
                    } catch (OutOfStockException e) {
                        return e;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> errors = futures.stream().map(StockReservationTest::get).toList();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long succeeded = errors.stream().filter(error -> error == null).count();
            long outOfStock = errors.stream().filter(OutOfStockException.class::isInstance).count();
            List<Throwable> unexpected = errors.stream()
                    .filter(error -> error != null && !(error instanceof OutOfStockException)).toList();
            assertThat(unexpected).isEmpty();
            log.info("{}: {} ok / {} out-of-stock, {} retries, 10 threads in {} ms",
                    name, succeeded, outOfStock, retries.get(), elapsedMillis);
            return new RunResult(succeeded, outOfStock, retries.get(), elapsedMillis);
        }
    }

    private void assertResult(Long productId, RunResult result, boolean optimistic) {
        assertThat(result.succeeded()).isEqualTo(5);
        assertThat(result.outOfStock()).isEqualTo(5);
        assertThat(products.findById(productId).orElseThrow().getStock()).isZero();
        assertThat(products.findById(productId).orElseThrow().getStock()).isGreaterThanOrEqualTo(0);
        if (optimistic) {
            assertThat(result.retries()).isGreaterThanOrEqualTo(0);
        }
    }

    private static Throwable get(Future<Throwable> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Reservation task failed", e);
        }
    }

    @FunctionalInterface
    private interface Reservation {
        int reserve();
    }

    private record RunResult(long succeeded, long outOfStock, int retries, long elapsedMillis) { }
}
