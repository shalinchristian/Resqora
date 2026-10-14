package com.resqora.resqora_backend.exception;

// Signed-in user typed the wrong current password: 403 (not 401, which the frontend treats as logout)
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException() {
        super("Current password is incorrect");
    }

    public IncorrectPasswordException(String message) {
        super(message);
    }
}
