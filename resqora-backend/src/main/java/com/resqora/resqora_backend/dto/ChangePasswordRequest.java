package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// newPassword follows the same rule as registration: 8+ characters, at least one letter and one digit
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,
        @NotBlank
        @Size(min = 8, max = 255)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String newPassword) {
}
