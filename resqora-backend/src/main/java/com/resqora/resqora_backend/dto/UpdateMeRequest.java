package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// body for PATCH /api/auth/me. both fields are optional, only the ones sent get changed.
// {"name": "..."} sets the name, {"aiEnabled": false} turns AI off
public record UpdateMeRequest(
        @Size(min = 1, max = 50)
        // no control characters (NUL, newlines, tabs...) in a name shown on screen
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name,
        Boolean aiEnabled) {

    public UpdateMeRequest {
        name = name == null ? null : name.trim();
    }

    // empty {} changes nothing so treat it as a mistake (400)
    @AssertTrue(message = "send name and/or aiEnabled")
    public boolean isAnyFieldPresent() {
        return name != null || aiEnabled != null;
    }
}
