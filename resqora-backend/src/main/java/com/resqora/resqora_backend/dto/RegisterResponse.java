package com.resqora.resqora_backend.dto;

// verificationRequired true -> the frontend shows the "enter the code we emailed you" screen
public record RegisterResponse(Long id, String email, String name, boolean emailVerified, boolean verificationRequired) {
    public RegisterResponse(Long id, String email, String name, boolean emailVerified) {
        this(id, email, name, emailVerified, !emailVerified);
    }
}
