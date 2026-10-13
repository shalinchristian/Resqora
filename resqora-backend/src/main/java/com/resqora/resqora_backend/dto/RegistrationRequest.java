package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        @Size(min = 8, max = 255)
        String password,
        @NotBlank
        @Size(min = 1, max = 100)
        String name) {

    // trim the name before validation runs, so "  Asha  " is stored as "Asha" and "   " counts as blank
    public RegistrationRequest {
        name = name == null ? null : name.trim();
    }
}