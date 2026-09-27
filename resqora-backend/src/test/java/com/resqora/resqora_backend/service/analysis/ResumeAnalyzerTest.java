package com.resqora.resqora_backend.service.analysis;

import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeAnalyzerTest {
    private final ResumeAnalyzer analyzer = new ResumeAnalyzer();

    @Test
    void completeResumeReceivesMaximumScore() {
        AnalysisResult result = analyzer.analyze(completeResume());

        assertEquals(100, result.overallScore());
        assertEquals(20, result.completenessScore());
        assertEquals(20, result.skillsScore());
        assertEquals(20, result.projectsScore());
        assertEquals(20, result.experienceScore());
        assertEquals(20, result.educationScore());
    }

    @Test
    void emptyResumeReceivesLowScoreWithFindings() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertEquals(10, result.overallScore());
        assertTrue(result.findings().stream().anyMatch(finding ->
                finding.category().equals("Skills") && finding.message().contains("No skills")));
        assertTrue(result.findings().stream().anyMatch(finding ->
                finding.category().equals("Experience") && finding.message().contains("No professional")));
    }

    @Test
    void skillsUseExplicitDistinctCountThresholds() {
        assertEquals(0, analyzer.analyze(withSkills(List.of())).skillsScore());
        assertEquals(8, analyzer.analyze(withSkills(List.of("Java", "SQL", "Git", "HTML"))).skillsScore());
        assertEquals(14, analyzer.analyze(withSkills(List.of("Java", "SQL", "Git", "HTML", "CSS"))).skillsScore());
        assertEquals(20, analyzer.analyze(withSkills(List.of(
                "Java", "SQL", "Git", "HTML", "CSS", "Spring", "Docker", "Linux", "Maven", "JPA"))).skillsScore());
    }

    @Test
    void projectsRewardDescriptionsAndMultipleEntries() {
        assertEquals(0, analyzer.analyze(withProjects(List.of())).projectsScore());
        assertEquals(8, analyzer.analyze(withProjects(List.of(new ProjectEntry("One", null)))).projectsScore());
        assertEquals(14, analyzer.analyze(withProjects(List.of(new ProjectEntry("One", "Details")))).projectsScore());
        assertEquals(20, analyzer.analyze(withProjects(List.of(
                new ProjectEntry("One", "Details"), new ProjectEntry("Two", "Details")))).projectsScore());
    }

    @Test
    void experienceWithoutEntriesRemainsReasonableForFreshers() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                "Alex", "alex@example.com", null, null, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertEquals(10, result.experienceScore());
        assertTrue(result.findings().stream().anyMatch(finding ->
                finding.message().contains("not treated as a failing score")));
    }

    @Test
    void educationScoreReflectsAvailableFields() {
        ParsedResume completeEducation = new ParsedResume(
                null, null, null, null, List.of(),
                List.of(new EducationEntry("Test University", "BSc", "2024", "Coursework")),
                List.of(), List.of(), List.of());
        ParsedResume incompleteEducation = new ParsedResume(
                null, null, null, null, List.of(),
                List.of(new EducationEntry("Test University", null, null, null)),
                List.of(), List.of(), List.of());

        assertEquals(20, analyzer.analyze(completeEducation).educationScore());
        assertEquals(10, analyzer.analyze(incompleteEducation).educationScore());
    }

    @Test
    void scoreIsBoundedAndRepeatable() {
        AnalysisResult first = analyzer.analyze(completeResume());
        AnalysisResult second = analyzer.analyze(completeResume());

        assertEquals(first, second);
        assertTrue(first.overallScore() >= 0 && first.overallScore() <= 100);
        assertTrue(first.completenessScore() >= 0 && first.completenessScore() <= 20);
        assertTrue(first.skillsScore() >= 0 && first.skillsScore() <= 20);
        assertTrue(first.projectsScore() >= 0 && first.projectsScore() <= 20);
        assertTrue(first.experienceScore() >= 0 && first.experienceScore() <= 20);
        assertTrue(first.educationScore() >= 0 && first.educationScore() <= 20);
    }

    private ParsedResume completeResume() {
        return new ParsedResume(
                "Alex Johnson", "alex.johnson@example.com", "+1 555-123-4567", "Test City",
                List.of("Java", "SQL", "Git", "HTML", "CSS", "Spring", "Docker", "Linux", "Maven", "JPA"),
                List.of(new EducationEntry("Test University", "BSc", "2024", "Coursework")),
                List.of(new ExperienceEntry("Example Co", "Developer", "2023-2024", "Built software"),
                        new ExperienceEntry("Sample Co", "Engineer", "2022-2023", "Maintained software")),
                List.of(new ProjectEntry("One", "Details"), new ProjectEntry("Two", "Details")),
                List.of("Cloud Certificate"));
    }

    private ParsedResume withSkills(List<String> skills) {
        return new ParsedResume(null, null, null, null, skills, List.of(), List.of(), List.of(), List.of());
    }

    private ParsedResume withProjects(List<ProjectEntry> projects) {
        return new ParsedResume(null, null, null, null, List.of(), List.of(), List.of(), projects, List.of());
    }
}