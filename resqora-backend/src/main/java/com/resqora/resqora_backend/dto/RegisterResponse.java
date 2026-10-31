package com.resqora.resqora_backend.dto;

// if verificationRequired is true the frontend shows the enter-code screen
public record RegisterResponse(Long id, String email, String name, boolean emailVerified, boolean verificationRequired) {
    public RegisterResponse(Long id, String email, String name, boolean emailVerified) {
        this(id, email, name, emailVerified, !emailVerified);
    }
}
