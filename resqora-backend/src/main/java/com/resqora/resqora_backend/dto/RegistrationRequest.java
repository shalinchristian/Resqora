package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        @Size(min = 8, max = 255)
        // at least one letter and one digit
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String password,
        @NotBlank
        @Size(min = 1, max = 50)
        // no control characters (NUL, newlines, tabs...) in a name shown on screen
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name) {

    // trim the name before validation runs, so "  Asha  " is stored as "Asha" and "   " counts as blank
    public RegistrationRequest {
        name = name == null ? null : name.trim();
        email = normalizeEmail(email);
    }

    // emails are case-insensitive: "  A@X.com " and "a@x.com" are the same account
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
