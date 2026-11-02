package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /api/auth/me body. Both fields are optional; only the ones sent are changed:
// {"name": "..."} sets the display name, {"aiEnabled": false} turns AI features off.
public record UpdateMeRequest(
        @Size(min = 1, max = 50)
        // no control characters (NUL, newlines, tabs...) in a name shown on screen
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String name,
        Boolean aiEnabled) {

    public UpdateMeRequest {
        name = name == null ? null : name.trim();
    }

    // an empty body {} changes nothing, so it is a mistake: 400
    @AssertTrue(message = "send name and/or aiEnabled")
    public boolean isAnyFieldPresent() {
        return name != null || aiEnabled != null;
    }
}
