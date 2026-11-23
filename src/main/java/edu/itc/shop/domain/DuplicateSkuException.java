
package edu.itc.shop.domain;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

public class DuplicateSkuException extends ClientErrorException {

    public DuplicateSkuException(String sku) {
        super("Duplicate SKU: " + sku, Response.Status.CONFLICT);
    }
}
