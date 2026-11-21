package edu.itc.shop.domain;

import jakarta.ws.rs.ClientErrorException;

public class InsufficientStockException extends ClientErrorException {
    public InsufficientStockException(long id, int stock, int delta) {
        super("Product " + id + " has stock " + stock + ", cannot apply delta " + delta, 409);
    }
}
