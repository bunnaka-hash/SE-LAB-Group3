package edu.itc.shop.common;

/** Base class for "this thing does not exist" errors, mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
