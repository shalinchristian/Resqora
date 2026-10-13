package com.resqora.resqora_backend.dto.match;

import java.util.List;

public record JobMatchResponse(int matchScore, List<String> matchedSkills, List<String> missingSkills) {
}
