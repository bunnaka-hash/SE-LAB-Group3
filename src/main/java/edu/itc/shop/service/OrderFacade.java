package edu.itc.shop.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import edu.itc.shop.domain.OrderStatus;

@Service
public class OrderFacade {

    private static final int MAX_ATTEMPTS = 3;

    private final OrderService orderService;

    public OrderFacade(OrderService orderService) { this.orderService = orderService; }

    /** Retries the whole transaction: each attempt reloads the order with the new version. */
    public void changeStatusWithRetry(Long id, OrderStatus next) {
        for (int attempt = 1; ; attempt++) {
            try {
                orderService.changeStatus(id, next);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backOff(attempt);
            }
        }
    }

    private static void backOff(int attempt) {
        try {
            Thread.sleep(10L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", ie);
        }
    }
}