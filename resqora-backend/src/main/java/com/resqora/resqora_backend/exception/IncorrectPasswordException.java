package com.resqora.resqora_backend.exception;

// logged in user typed the wrong current password. 403, not 401 (the frontend logs out on 401)
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("Current password is incorrect");
    }

    public IncorrectPasswordException(String message) {
        super(message);
    }
}
