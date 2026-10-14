package com.resqora.resqora_backend.dto.jobs;

import java.util.List;

public record JobSuggestionsResponse(List<JobSuggestion> jobs, boolean cached) {
}
