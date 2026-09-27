package com.resqora.resqora_backend.model.analysis;

import java.util.List;

public record AnalysisResult(
        int overallScore,
        int completenessScore,
        int skillsScore,
        int projectsScore,
        int experienceScore,
        int educationScore,
        List<AnalysisFinding> findings) {

    public AnalysisResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}