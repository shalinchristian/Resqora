package com.resqora.resqora_backend.exception;

// 400: wrong, expired, used-up or missing code. One message for all cases, so it reveals nothing.
public class InvalidCodeException extends RuntimeException {
    public InvalidCodeException() {
        super("Invalid or expired code");
    }
}
