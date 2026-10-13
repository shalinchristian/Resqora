package com.resqora.resqora_backend.dto.resume;

import java.util.List;

public record ResumeListResponse(int count, int limit, List<ResumeSummary> resumes) {
}
