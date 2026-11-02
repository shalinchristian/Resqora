package com.resqora.resqora_backend.service.analysis;

import com.resqora.resqora_backend.model.analysis.AnalysisFinding;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // ---- findings v2: a fix for every warning, no overlaps, correct plurals ----

    @Test
    void everyWarningHasAConcreteFixAndASection() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                null, null, null, null, List.of("Java"),
                List.of(new EducationEntry("Test University", null, null, null)),
                List.of(new ExperienceEntry("Example Co", "Intern", null, "Built software")),
                List.of(new ProjectEntry("Chat App", null)), List.of()), "x".repeat(400));
        List<AnalysisFinding> warnings = result.findings().stream().filter(f -> "WARNING".equals(f.type())).toList();
        assertTrue(warnings.size() >= 5, warnings.toString());
        for (AnalysisFinding warning : warnings) {
            assertNotNull(warning.fix(), warning.toString());
            assertFalse(warning.fix().isBlank(), warning.toString());
            assertNotNull(warning.section(), warning.toString());
        }
        // INFO findings are status lines without a fix
        result.findings().stream().filter(f -> "INFO".equals(f.type()))
                .forEach(info -> assertEquals(null, info.fix(), info.toString()));
    }

    @Test
    void sameProblemIsReportedOnlyOnce() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                "Alex", "alex@example.com", null, null, List.of("Java"),
                List.of(new EducationEntry("Test University", "BSc", "2024", "CGPA 8.1")),
                List.of(),
                List.of(new ProjectEntry("Chat App", null), new ProjectEntry("Chat App", null)), List.of()));
        assertEquals(result.findings().size(), result.findings().stream().distinct().count());
        assertEquals(1, result.findings().stream().filter(f -> f.message().contains("Chat App")).count());
        // a missing phone used to appear twice (score line + separate line finding)
        assertEquals(1, result.findings().stream()
                .filter(f -> (f.message() + f.fix()).toLowerCase().contains("phone")).count());
        // the projects summary has nothing new to add (2 projects; descriptions are covered per project)
        AnalysisFinding projects = result.findings().stream()
                .filter(f -> "Projects".equals(f.category()) && f.line() == null).findFirst().orElseThrow();
        assertEquals("INFO", projects.type());
        assertEquals("Projects section contains 2 projects, 0 with descriptions.", projects.message());
    }

    @Test
    void countsArePluralisedCorrectly() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                "Alex", "alex@example.com", "+91 98765 43210", null, List.of("Java"), List.of(),
                List.of(new ExperienceEntry("Example Co", "Intern", "2024", "Built 3 services")),
                List.of(new ProjectEntry("One", "Details")), List.of()));
        List<String> messages = result.findings().stream().map(AnalysisFinding::message).toList();
        assertTrue(messages.contains("Experience section contains 1 entry, 1 with a description."), messages.toString());
        assertTrue(messages.contains("Projects section contains 1 project, 1 with a description."), messages.toString());
        assertTrue(messages.contains("Skills section lists 1 distinct technical skill."), messages.toString());
        assertTrue(messages.stream().noneMatch(m -> m.contains("1 entries") || m.contains("1 projects")));
        AnalysisFinding skills = result.findings().stream()
                .filter(f -> "Skills".equals(f.category())).findFirst().orElseThrow();
        assertTrue(skills.fix().startsWith("Add 9 more technical skills"), skills.fix());
    }

    @Test
    void lineFindingsPointAtTheLineAndSection() {
        AnalysisResult result = analyzer.analyze(new ParsedResume(
                "Alex", "alex@example.com", "+91 98765 43210", null, List.of("Java"), List.of(),
                List.of(new ExperienceEntry("Example Co", "Intern", null, "Built services")), List.of(), List.of()));
        AnalysisFinding dates = result.findings().stream()
                .filter(f -> f.message().startsWith("Add start and end dates")).findFirst().orElseThrow();
        assertEquals("Example Co | Intern", dates.line());
        assertEquals("Experience", dates.section());
        assertTrue(dates.fix().contains("Jan 2025"));
    }

    @Test
    void findingsVersionIsBumpedSoOldSavedAnalysesAreRecomputed() {
        assertEquals(2, ResumeAnalyzer.VERSION);
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