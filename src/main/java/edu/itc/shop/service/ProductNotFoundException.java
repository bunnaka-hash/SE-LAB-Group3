package edu.itc.shop.service;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(long id) {
        super("Product " + id + " not found");
    }
}
