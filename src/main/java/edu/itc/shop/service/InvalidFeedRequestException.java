package edu.itc.shop.service;

public class InvalidFeedRequestException extends RuntimeException {
    public InvalidFeedRequestException(String message) {
        super(message);
    }
}
