package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// same password rules as registration
public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code,
        @NotBlank
        @Size(min = 8, max = 255)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String newPassword) {
    public ResetPasswordRequest {
        email = RegistrationRequest.normalizeEmail(email);
        code = code == null ? null : code.trim();
    }
}
