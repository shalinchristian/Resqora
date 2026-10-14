package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        @Size(max = 255)
        String password) {

    // same normalisation as registration; the password length rule is not revealed at login
    public LoginRequest {
        email = RegistrationRequest.normalizeEmail(email);
    }
}