package com.resqora.resqora_backend.dto.resume;

import java.time.Instant;

public record ResumeSummary(
        Long id,
        String originalFileName,
        String documentType,
        Instant uploadedAt,
        Integer overallScore,
        // most important saved finding (null if not analysed yet or nothing to fix)
        String topFinding,
        // true when the original file is stored (GET /api/resumes/{id}/file works)
        boolean hasFile) {
}
