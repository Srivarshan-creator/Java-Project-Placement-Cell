package com.placement.exception;

public class SingleOfferViolationException extends RuntimeException {
    public SingleOfferViolationException(String message) {
        super(message);
    }
}

