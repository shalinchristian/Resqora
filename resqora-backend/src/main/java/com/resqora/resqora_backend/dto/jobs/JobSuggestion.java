package com.resqora.resqora_backend.dto.jobs;

import java.util.List;

public record JobSuggestion(
        String title,
        String company,
        String location,
        int matchScore,
        List<String> matchedSkills,
        String url) {
}
