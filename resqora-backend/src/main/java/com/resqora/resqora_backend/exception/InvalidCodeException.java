package com.resqora.resqora_backend.exception;

// 400 for a wrong, expired, used or missing code. same message for all of them so it gives nothing away
public class InvalidCodeException extends RuntimeException {
    public InvalidCodeException() {
        super("Invalid or expired code");
    }
}
