package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /api/auth/me body: lets users (including older accounts with no name) set their name
public record UpdateNameRequest(
        @NotBlank
        @Size(min = 1, max = 50)
        // no control characters (NUL, newlines, tabs...) in a name shown on screen
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name) {

    public UpdateNameRequest {
        name = name == null ? null : name.trim();
    }
}
