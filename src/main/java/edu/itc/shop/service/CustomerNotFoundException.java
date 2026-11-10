package edu.itc.shop.service;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String email) {
        super("No customer with email " + email);
    }
}
