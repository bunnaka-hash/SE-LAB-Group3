package edu.itc.shop.repository;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(Long productId, int quantity) {
        super("Product " + productId + " does not have " + quantity + " units available");
    }
}
