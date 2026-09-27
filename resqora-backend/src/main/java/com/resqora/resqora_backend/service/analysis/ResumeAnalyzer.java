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

@Service
public class ResumeAnalyzer {
    public AnalysisResult analyze(ParsedResume resume) {
        List<AnalysisFinding> findings = new ArrayList<>();
        int completenessScore = scoreCompleteness(resume, findings);
        int skillsScore = scoreSkills(resume, findings);
        int projectsScore = scoreProjects(resume, findings);
        int experienceScore = scoreExperience(resume, findings);
        int educationScore = scoreEducation(resume, findings);
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
            if (present(skill)) {
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