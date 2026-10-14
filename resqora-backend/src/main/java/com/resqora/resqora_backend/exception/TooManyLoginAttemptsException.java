package com.resqora.resqora_backend.exception;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Too many login attempts, try again later");
    }
}
