package com.resqora.resqora_backend.exception;

public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException() {
        super("Authenticated user was not found");
    }
}