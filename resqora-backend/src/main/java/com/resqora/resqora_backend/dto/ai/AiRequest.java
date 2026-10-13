package com.resqora.resqora_backend.dto.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// jobDescription is only required for SHOULD_I_APPLY (checked in AiService)
public record AiRequest(
        @NotNull
        AiAction action,
        @Size(max = 10000)
        String jobDescription) {
}
