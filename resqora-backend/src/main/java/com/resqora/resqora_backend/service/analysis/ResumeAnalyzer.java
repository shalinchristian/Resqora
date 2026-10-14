package com.resqora.resqora_backend.service.analysis;

import com.resqora.resqora_backend.model.analysis.AnalysisFinding;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ResumeAnalyzer {
    private static final Set<String> SOFT_SKILLS = Set.of(
            "communication", "communication skills", "leadership", "teamwork", "team work", "team player",
            "problem solving", "problem-solving", "critical thinking", "time management", "adaptability",
            "collaboration", "creativity", "work ethic", "interpersonal skills", "attention to detail",
            "self-motivated", "hardworking", "hard working", "quick learner", "multitasking", "presentation");
    private static final Pattern METRIC = Pattern.compile("\\d");

    // Feedback that names the exact entry to fix. Score-neutral; ordered most useful first.
    private void addLineFindings(ParsedResume resume, List<AnalysisFinding> findings) {
        if (resume == null) {
            return;
        }
        for (ExperienceEntry entry : resume.experience() == null ? List.<ExperienceEntry>of() : resume.experience()) {
            String line = entryLine(entry.company(), entry.title(), entry.dates());
            if (!present(entry.dates())) {
                findings.add(new AnalysisFinding("Experience", "WARNING",
                        "Add start and end dates to \"" + line + "\".", line));
            }
            if (!present(entry.description())) {
                findings.add(new AnalysisFinding("Experience", "WARNING",
                        "Add 1-2 bullet points describing your work at \"" + line + "\".", line));
            } else if (!METRIC.matcher(entry.description()).find()) {
                findings.add(new AnalysisFinding("Experience", "WARNING",
                        "Add a number to a bullet under \"" + line + "\" (users, %, time saved, money).", line));
            }
        }
        for (ProjectEntry project : resume.projects() == null ? List.<ProjectEntry>of() : resume.projects()) {
            if (present(project.name()) && !present(project.description())) {
                findings.add(new AnalysisFinding("Projects", "WARNING",
                        "Add a one-line description under project \"" + project.name() + "\".", project.name()));
            }
        }
        for (EducationEntry entry : resume.education() == null ? List.<EducationEntry>of() : resume.education()) {
            String line = entryLine(entry.institution(), entry.degree(), null);
            if (!present(entry.dates()) && present(line)) {
                findings.add(new AnalysisFinding("Education", "WARNING",
                        "Add dates to \"" + line + "\".", line));
            }
        }
        if (!present(resume.phone())) {
            findings.add(new AnalysisFinding("Completeness", "WARNING",
                    "Add a phone number to your contact line.", null));
        }
    }

    private String entryLine(String first, String second, String third) {
        return java.util.stream.Stream.of(first, second, third)
                .filter(this::present)
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    public static final String LAYOUT_CATEGORY = "Layout";
    public static final String UNREADABLE_LAYOUT_MESSAGE = "We couldn't read this resume's layout reliably, "
            + "so the score may be inaccurate. Try a simple single-column PDF or DOCX.";
    static final int UNREADABLE_MIN_CHARACTERS = 300;

    public AnalysisResult analyze(ParsedResume resume) {
        return analyze(resume, null);
    }

    /**
     * Same scoring; with the extracted text it also checks extraction quality. Plenty of text but
     * 3 or more of skills/education/experience/projects empty means the layout was not understood,
     * so a "Layout" WARNING goes first (the score is still returned, status codes unchanged).
     */
    public AnalysisResult analyze(ParsedResume resume, String extractedText) {
        List<AnalysisFinding> findings = new ArrayList<>();
        if (looksUnreadable(resume, extractedText)) {
            findings.add(finding(LAYOUT_CATEGORY, "WARNING", UNREADABLE_LAYOUT_MESSAGE));
        }
        int completenessScore = scoreCompleteness(resume, findings);
        int skillsScore = scoreSkills(resume, findings);
        int projectsScore = scoreProjects(resume, findings);
        int experienceScore = scoreExperience(resume, findings);
        int educationScore = scoreEducation(resume, findings);
        addLineFindings(resume, findings);
        int overallScore = completenessScore + skillsScore + projectsScore
                + experienceScore + educationScore;

        return new AnalysisResult(
            clampOverall(overallScore),
                clamp(completenessScore),
                clamp(skillsScore),
                clamp(projectsScore),
                clamp(experienceScore),
                clamp(educationScore),
                findings);
    }

    static boolean looksUnreadable(ParsedResume resume, String extractedText) {
        if (resume == null || extractedText == null) {
            return false;
        }
        long visible = extractedText.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        int emptyCore = (isEmpty(resume.skills()) ? 1 : 0) + (isEmpty(resume.education()) ? 1 : 0)
                + (isEmpty(resume.experience()) ? 1 : 0) + (isEmpty(resume.projects()) ? 1 : 0);
        return visible >= UNREADABLE_MIN_CHARACTERS && emptyCore >= 3;
    }

    private static boolean isEmpty(List<?> items) {
        return items == null || items.isEmpty();
    }

    private int scoreCompleteness(ParsedResume resume, List<AnalysisFinding> findings) {
        if (resume == null) {
            findings.add(finding("Completeness", "WARNING", "Resume data is missing."));
            return 0;
        }

        int score = 0;
        score += present(resume.name()) ? 4 : 0;
        score += present(resume.email()) ? 4 : 0;
        score += present(resume.phone()) ? 3 : 0;
        score += hasItems(resume.skills()) ? 3 : 0;
        score += hasItems(resume.education()) ? 3 : 0;
        score += hasItems(resume.projects()) ? 3 : 0;
        findings.add(finding("Completeness", score == 20 ? "INFO" : "WARNING",
                "Completeness score is " + score + " out of 20."));
        return score;
    }

    private int scoreSkills(ParsedResume resume, List<AnalysisFinding> findings) {
        // soft skills ("Communication", "Leadership") are listed but do not count toward the skill score
        int count = distinctSkillCount(resume == null ? List.of() : resume.skills());
        int score = count == 0 ? 0 : count <= 4 ? 8 : count <= 9 ? 14 : 20;
        String message = count == 0
                ? "No skills were detected."
                : "Skills section contains " + count + " distinct skills.";
        findings.add(finding("Skills", score == 20 ? "INFO" : "WARNING", message));
        return score;
    }

    private int scoreProjects(ParsedResume resume, List<AnalysisFinding> findings) {
        List<ProjectEntry> projects = resume == null ? List.of() : resume.projects();
        long describedProjects = projects.stream().filter(project -> present(project.description())).count();
        int score = projects.isEmpty() ? 0 : describedProjects == 0 ? 8 : projects.size() == 1 ? 14 : 20;
        String message = projects.isEmpty()
                ? "No projects were detected."
                : "Projects section contains " + projects.size() + " projects, "
                + describedProjects + " with descriptions.";
        findings.add(finding("Projects", score == 20 ? "INFO" : "WARNING", message));
        return score;
    }

    private int scoreExperience(ParsedResume resume, List<AnalysisFinding> findings) {
        List<ExperienceEntry> experience = resume == null ? List.of() : resume.experience();
        long describedEntries = experience.stream()
                .filter(entry -> present(entry.description()))
                .count();
        int score = experience.isEmpty() ? 10 : describedEntries == 0 ? 12 : experience.size() == 1 ? 16 : 20;
        String message = experience.isEmpty()
                ? "No professional experience entries were detected; this is not treated as a failing score."
                : "Experience section contains " + experience.size() + " entries, "
                + describedEntries + " with descriptions.";
        findings.add(finding("Experience", experience.isEmpty() ? "INFO" : score == 20 ? "INFO" : "WARNING", message));
        return score;
    }

    private int scoreEducation(ParsedResume resume, List<AnalysisFinding> findings) {
        List<EducationEntry> education = resume == null ? List.of() : resume.education();
        if (education.isEmpty()) {
            findings.add(finding("Education", "WARNING", "No education entries were detected."));
            return 0;
        }

        int score = 5;
        boolean institutionPresent = education.stream().anyMatch(entry -> present(entry.institution()));
        boolean degreePresent = education.stream().anyMatch(entry -> present(entry.degree()));
        boolean datesPresent = education.stream().anyMatch(entry -> present(entry.dates()));
        boolean detailsPresent = education.stream().anyMatch(entry -> present(entry.details()));
        score += institutionPresent ? 5 : 0;
        score += degreePresent ? 5 : 0;
        score += datesPresent ? 3 : 0;
        score += detailsPresent ? 2 : 0;
        findings.add(finding("Education", score == 20 ? "INFO" : "WARNING",
                "Education information is " + score + " out of 20 complete."));
        return score;
    }

    private int distinctSkillCount(List<String> skills) {
        Set<String> distinctSkills = new HashSet<>();
        for (String skill : skills) {
            if (present(skill) && !SOFT_SKILLS.contains(skill.trim().toLowerCase(Locale.ROOT))) {
                distinctSkills.add(skill.trim().toLowerCase(Locale.ROOT));
            }
        }
        return distinctSkills.size();
    }

    private boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private AnalysisFinding finding(String category, String type, String message) {
        return new AnalysisFinding(category, type, message);
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(20, score));
    }

    private int clampOverall(int score) {
        return Math.max(0, Math.min(100, score));
    }
}