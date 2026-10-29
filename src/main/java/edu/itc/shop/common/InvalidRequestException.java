package edu.itc.shop.common;

/** A request that is well formed but breaks a business rule, mapped to 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
