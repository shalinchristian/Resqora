package com.resqora.resqora_backend.dto.resume;

import java.time.Instant;

public record ResumeSummary(
        Long id,
        String originalFileName,
        String documentType,
        Instant uploadedAt) {
}
