package com.resqora.resqora_backend.dto.match;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobMatchRequest(
        @NotBlank
        @Size(max = 10000)
        String jobDescription) {
}
