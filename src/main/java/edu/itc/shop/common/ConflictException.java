package edu.itc.shop.common;

/** Base class for "the request clashes with the current state" errors, mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
