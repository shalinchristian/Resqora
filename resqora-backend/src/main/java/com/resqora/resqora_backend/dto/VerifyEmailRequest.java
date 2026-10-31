package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code) {
    public VerifyEmailRequest {
        email = RegistrationRequest.normalizeEmail(email);
        code = code == null ? null : code.trim();
    }
}
