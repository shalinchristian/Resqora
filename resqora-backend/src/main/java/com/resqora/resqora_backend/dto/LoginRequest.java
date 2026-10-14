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

    // same cleanup as register. don't reveal the password length rule at login
    public LoginRequest {
        email = RegistrationRequest.normalizeEmail(email);
    }
}