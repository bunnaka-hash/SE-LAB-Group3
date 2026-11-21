package edu.itc.shop.domain;

import jakarta.ws.rs.ClientErrorException;

public class DuplicateSkuException extends ClientErrorException {
    public DuplicateSkuException(String sku) {
        super("SKU " + sku + " already exists", 409);
    }
}
