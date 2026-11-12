package edu.itc.shop.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.OrderStatus;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.support.TestData;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingTest {

    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate tx;
    @Autowired TestData data;
    @Autowired OrderFacade facade;

    /** Outcome of one writer thread: the status it tried to set and the failure, if any. */
    record Result(OrderStatus target, Throwable error) { }

    @Test
    void secondWriterFailsWithOptimisticLock() throws Exception {
        Long orderId = data.newOrder();
        long versionBefore = orders.findById(orderId).orElseThrow().getVersion();
        CountDownLatch bothLoaded = new CountDownLatch(2);

        Function<OrderStatus, Callable<Result>> writer = target -> () -> {
            try {
                tx.executeWithoutResult(status -> {
                    Order o = orders.findById(orderId).orElseThrow(); // both read the same version
                    bothLoaded.countDown();
                    await(bothLoaded);
                    o.setStatus(target);                              // dirty -> UPDATE at commit
                });
                return new Result(target, null);
            } catch (RuntimeException e) {
                return new Result(target, e);
            }
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Result>> futures = pool.invokeAll(List.of(
                    writer.apply(OrderStatus.PAID),
                    writer.apply(OrderStatus.CANCELLED)));

            List<Result> results = futures.stream().map(OptimisticLockingTest::get).toList();
            List<Result> failures = results.stream().filter(r -> r.error() != null).toList();
            List<Result> winners = results.stream().filter(r -> r.error() == null).toList();

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).error())
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(winners).hasSize(1);

            Order after = orders.findById(orderId).orElseThrow();
            assertThat(after.getVersion()).isEqualTo(versionBefore + 1);
            assertThat(after.getStatus()).isEqualTo(winners.get(0).target());
        }
    }

    @Test
    void facadeRetriesSoBothWritersEventuallySucceed() throws Exception {
        Long orderId = data.newOrder();
        long versionBefore = orders.findById(orderId).orElseThrow().getVersion();
        CyclicBarrier start = new CyclicBarrier(2);

        Function<OrderStatus, Callable<Throwable>> writer = target -> () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                facade.changeStatusWithRetry(orderId, target);
                return null;
            } catch (Exception e) {
                return e;
            }
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Throwable>> futures = pool.invokeAll(List.of(
                    writer.apply(OrderStatus.PAID),
                    writer.apply(OrderStatus.SHIPPED)));

            // both succeed (a collision on the first attempt is retried)
            assertThat(futures.stream().map(OptimisticLockingTest::get)).containsOnlyNulls();

            Order after = orders.findById(orderId).orElseThrow();
            assertThat(after.getVersion()).isEqualTo(versionBefore + 2);
            assertThat(after.getStatus()).isIn(OrderStatus.PAID, OrderStatus.SHIPPED);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}