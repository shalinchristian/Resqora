package com.resqora.resqora_backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// One earlier turn sent back by the browser. Untrusted: it is only ever passed to the AI as quoted data.
public record ChatMessage(
        @NotNull @Pattern(regexp = "user|model", message = "must be user or model") String role,
        @NotBlank @Size(max = 500) @Pattern(regexp = NO_CONTROL_CHARS, message = NO_CONTROL_CHARS_MESSAGE) String text) {
    // newlines and tabs are fine in chat; NUL and other control characters are not
    public static final String NO_CONTROL_CHARS = "^(?:[^\\p{Cntrl}]|[\\r\\n\\t])*$";
    public static final String NO_CONTROL_CHARS_MESSAGE = "must not contain control characters";
}
