package com.resqora.resqora_backend.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @NotBlank @Size(max = 500)
        @Pattern(regexp = ChatMessage.NO_CONTROL_CHARS, message = ChatMessage.NO_CONTROL_CHARS_MESSAGE)
        String message,
        // last turns only; more than 6 is rejected with 400
        @Size(max = 6) List<@NotNull @Valid ChatMessage> history,
        @Size(max = 5000) String jobDescription) {
}
