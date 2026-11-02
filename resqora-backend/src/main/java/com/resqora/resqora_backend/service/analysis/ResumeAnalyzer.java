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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rule-based resume scoring: five sections, 20 points each, same input = same score and findings.
 *
 * Findings rules (version 2):
 * - every WARNING has one concrete fix, and the section (and line, when there is one) to edit;
 * - no two findings say the same thing: a section summary only carries a fix for what the
 *   line-level findings below it do not already cover (otherwise it is INFO, a status line);
 * - identical findings (e.g. the same entry written twice) are shown once.
 */
@Service
public class ResumeAnalyzer {
    // Bump when the findings or scores change: saved analyses from an older version are recomputed.
    public static final int VERSION = 2;

    public static final String LAYOUT_CATEGORY = "Layout";
    public static final String UNREADABLE_LAYOUT_MESSAGE = "We couldn't read this resume's layout reliably, "
            + "so the score may be inaccurate. Try a simple single-column PDF or DOCX.";
    static final int UNREADABLE_MIN_CHARACTERS = 300;

    private static final Set<String> SOFT_SKILLS = Set.of(
            "communication", "communication skills", "leadership", "teamwork", "team work", "team player",
            "problem solving", "problem-solving", "critical thinking", "time management", "adaptability",
            "collaboration", "creativity", "work ethic", "interpersonal skills", "attention to detail",
            "self-motivated", "hardworking", "hard working", "quick learner", "multitasking", "presentation");
    private static final Pattern METRIC = Pattern.compile("\\d");
    private static final int FULL_MARKS_SKILLS = 10;

    private static final String WARNING = "WARNING";
    private static final String INFO = "INFO";

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
            findings.add(new AnalysisFinding(LAYOUT_CATEGORY, WARNING, UNREADABLE_LAYOUT_MESSAGE, null, null,
                    "Save the resume as a single-column PDF or DOCX with plain headings (Skills, Education, "
                            + "Experience, Projects), no tables or text boxes, and upload it again."));
        }
        int completenessScore = scoreCompleteness(resume, findings);
        int skillsScore = scoreSkills(resume, findings);
        int projectsScore = scoreProjects(resume, findings);
        int experienceScore = scoreExperience(resume, findings);
        int educationScore = scoreEducation(resume, findings);
        int overallScore = completenessScore + skillsScore + projectsScore + experienceScore + educationScore;

        return new AnalysisResult(
                clampOverall(overallScore),
                clamp(completenessScore),
                clamp(skillsScore),
                clamp(projectsScore),
                clamp(experienceScore),
                clamp(educationScore),
                List.copyOf(new java.util.LinkedHashSet<>(findings)));
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

    // Name 4 + email 4 + phone 3 + skills 3 + education 3 + projects 3 = 20.
    // Missing sections are reported (with their fix) by their own section, so here only contact details
    // get a fix; otherwise the same gap would be listed twice.
    private int scoreCompleteness(ParsedResume resume, List<AnalysisFinding> findings) {
        if (resume == null) {
            findings.add(new AnalysisFinding("Completeness", WARNING, "Resume data is missing.", null, null,
                    "Upload a text-based PDF or DOCX resume."));
            return 0;
        }
        int score = 0;
        score += present(resume.name()) ? 4 : 0;
        score += present(resume.email()) ? 4 : 0;
        score += present(resume.phone()) ? 3 : 0;
        score += hasItems(resume.skills()) ? 3 : 0;
        score += hasItems(resume.education()) ? 3 : 0;
        score += hasItems(resume.projects()) ? 3 : 0;

        Map<String, String> missingContact = new LinkedHashMap<>();
        if (!present(resume.name())) {
            missingContact.put("name", "your full name as the first line");
        }
        if (!present(resume.email())) {
            missingContact.put("email address", "an email address you check (e.g. name@gmail.com)");
        }
        if (!present(resume.phone())) {
            missingContact.put("phone number", "a phone number with country code (e.g. +91 98765 43210)");
        }
        String scoreText = "Completeness is " + score + " out of 20";
        if (score == 20) {
            findings.add(new AnalysisFinding("Completeness", INFO,
                    scoreText + ": contact details and the main sections are all present.", null, null, null));
        } else if (!missingContact.isEmpty()) {
            findings.add(new AnalysisFinding("Completeness", WARNING,
                    scoreText + ": no " + joinWords(List.copyOf(missingContact.keySet())) + " found.",
                    null, "Contact",
                    "Add " + joinWords(List.copyOf(missingContact.values())) + " to the contact line at the top."));
        } else {
            findings.add(new AnalysisFinding("Completeness", INFO,
                    scoreText + " because a main section is missing; its fix is listed under that section.",
                    null, null, null));
        }
        return score;
    }

    private int scoreSkills(ParsedResume resume, List<AnalysisFinding> findings) {
        // soft skills ("Communication", "Leadership") are listed but do not count toward the skill score
        int count = distinctSkillCount(resume == null ? List.of() : resume.skills());
        int score = count == 0 ? 0 : count <= 4 ? 8 : count <= 9 ? 14 : 20;
        if (count == 0) {
            findings.add(new AnalysisFinding("Skills", WARNING, "No skills were detected.", null, "Skills",
                    "Add a \"Skills\" heading with at least " + FULL_MARKS_SKILLS + " technical skills separated "
                            + "by commas, e.g. \"Java, Spring Boot, SQL, Git, React\"."));
        } else if (score < 20) {
            int more = FULL_MARKS_SKILLS - count;
            findings.add(new AnalysisFinding("Skills", WARNING,
                    "Skills section lists " + plural(count, "distinct technical skill", "distinct technical skills")
                            + ".", null, "Skills",
                    "Add " + plural(more, "more technical skill", "more technical skills")
                            + " you have really used (languages, frameworks, databases, tools); "
                            + "soft skills such as Communication are not counted."));
        } else {
            findings.add(new AnalysisFinding("Skills", INFO,
                    "Skills section lists " + count + " distinct technical skills.", null, "Skills", null));
        }
        return score;
    }

    private int scoreProjects(ParsedResume resume, List<AnalysisFinding> findings) {
        List<ProjectEntry> projects = resume == null ? List.of() : resume.projects();
        long describedProjects = projects.stream().filter(project -> present(project.description())).count();
        int score = projects.isEmpty() ? 0 : describedProjects == 0 ? 8 : projects.size() == 1 ? 14 : 20;
        if (projects.isEmpty()) {
            findings.add(new AnalysisFinding("Projects", WARNING, "No projects were detected.", null, "Projects",
                    "Add a \"Projects\" heading with 2 projects: the project name on one line, then a line saying "
                            + "what it does and the tech used."));
            return score;
        }
        String message = "Projects section contains " + plural(projects.size(), "project", "projects") + ", "
                + describedProjects + " with " + (describedProjects == 1 ? "a description" : "descriptions") + ".";
        // a missing description is reported per project below; here only "add a second project" is new
        String fix = projects.size() == 1
                ? "Add a second project (course, hackathon or personal) with a one-line description."
                : null;
        findings.add(new AnalysisFinding("Projects", fix == null ? INFO : WARNING, message, null, "Projects", fix));
        for (ProjectEntry project : projects) {
            if (present(project.name()) && !present(project.description())) {
                findings.add(new AnalysisFinding("Projects", WARNING,
                        "Add a one-line description under project \"" + project.name() + "\".",
                        project.name(), "Projects",
                        "Under \"" + project.name() + "\" add one line: what it does and the main tech, "
                                + "e.g. \"REST API for event bookings using Spring Boot and PostgreSQL\"."));
            }
        }
        return score;
    }

    private int scoreExperience(ParsedResume resume, List<AnalysisFinding> findings) {
        List<ExperienceEntry> experience = resume == null ? List.of() : resume.experience();
        long describedEntries = experience.stream().filter(entry -> present(entry.description())).count();
        int score = experience.isEmpty() ? 10 : describedEntries == 0 ? 12 : experience.size() == 1 ? 16 : 20;
        if (experience.isEmpty()) {
            findings.add(new AnalysisFinding("Experience", INFO,
                    "No professional experience entries were detected; this is not treated as a failing score.",
                    null, "Experience", null));
            return score;
        }
        String message = "Experience section contains " + plural(experience.size(), "entry", "entries") + ", "
                + describedEntries + " with " + (describedEntries == 1 ? "a description" : "descriptions") + ".";
        // missing descriptions are reported per entry below; here only "add a second role" is new
        String fix = experience.size() == 1 && describedEntries == 1
                ? "Add a second role, such as an internship, part-time job, freelance or open-source work."
                : null;
        findings.add(new AnalysisFinding("Experience", fix == null ? INFO : WARNING, message, null, "Experience",
                fix));
        for (ExperienceEntry entry : experience) {
            String line = entryLine(entry.company(), entry.title(), entry.dates());
            if (!present(entry.dates())) {
                findings.add(new AnalysisFinding("Experience", WARNING,
                        "Add start and end dates to \"" + line + "\".", line, "Experience",
                        "Write the dates on the same line as the role, e.g. \"Jun 2024 – Aug 2024\" "
                                + "or \"Jan 2025 – Present\"."));
            }
            if (!present(entry.description())) {
                findings.add(new AnalysisFinding("Experience", WARNING,
                        "Add 1-2 bullet points describing your work at \"" + line + "\".", line, "Experience",
                        "Under this role add 1-2 bullets that start with a verb (Built, Designed, Fixed) and "
                                + "say what you did and with which tools."));
            } else if (!METRIC.matcher(entry.description()).find()) {
                findings.add(new AnalysisFinding("Experience", WARNING,
                        "Add a number to a bullet under \"" + line + "\" (users, %, time saved, money).", line,
                        "Experience",
                        "Rewrite one bullet with a measurable result, e.g. \"cut page load time by 40%\" or "
                                + "\"used by 200+ students\"."));
            }
        }
        return score;
    }

    // 5 for having education + institution 5 + degree 5 + dates 3 + details (CGPA, coursework) 2 = 20
    private int scoreEducation(ParsedResume resume, List<AnalysisFinding> findings) {
        List<EducationEntry> education = resume == null ? List.of() : resume.education();
        if (education.isEmpty()) {
            findings.add(new AnalysisFinding("Education", WARNING, "No education entries were detected.", null,
                    "Education", "Add an \"Education\" heading with the institution, degree and years, "
                            + "e.g. \"B.Tech Computer Engineering, 2022 – 2026\"."));
            return 0;
        }
        boolean institutionPresent = education.stream().anyMatch(entry -> present(entry.institution()));
        boolean degreePresent = education.stream().anyMatch(entry -> present(entry.degree()));
        boolean datesPresent = education.stream().anyMatch(entry -> present(entry.dates()));
        boolean detailsPresent = education.stream().anyMatch(entry -> present(entry.details()));
        int score = 5 + (institutionPresent ? 5 : 0) + (degreePresent ? 5 : 0) + (datesPresent ? 3 : 0)
                + (detailsPresent ? 2 : 0);

        List<AnalysisFinding> lineFindings = new ArrayList<>();
        boolean datesCoveredByLines = true;
        for (EducationEntry entry : education) {
            String line = entryLine(entry.institution(), entry.degree(), null);
            if (!present(entry.dates())) {
                if (present(line)) {
                    lineFindings.add(new AnalysisFinding("Education", WARNING, "Add dates to \"" + line + "\".",
                            line, "Education",
                            "Add the start and end years on this line, e.g. \"2022 – 2026\" or \"Expected 2026\"."));
                } else {
                    datesCoveredByLines = false;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        if (!institutionPresent) {
            missing.add("the institution name");
        }
        if (!degreePresent) {
            missing.add("your degree (e.g. B.Tech Computer Engineering)");
        }
        if (!datesPresent && !datesCoveredByLines) {
            missing.add("the start and end years");
        }
        if (!detailsPresent) {
            missing.add("one detail line such as CGPA or relevant coursework");
        }
        String message = "Education information is " + score + " out of 20 complete.";
        String fix = missing.isEmpty() ? null : "Add " + joinWords(missing) + " to your education entry.";
        findings.add(new AnalysisFinding("Education", fix == null ? INFO : WARNING, message, null, "Education", fix));
        findings.addAll(lineFindings);
        return score;
    }

    private static String entryLine(String first, String second, String third) {
        return Stream.of(first, second, third).filter(ResumeAnalyzer::present).collect(Collectors.joining(" | "));
    }

    // "1 entry", "2 entries"
    static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    // "a", "a and b", "a, b and c"
    static String joinWords(List<String> words) {
        if (words.size() <= 1) {
            return String.join("", words);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
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

    private static boolean isEmpty(List<?> items) {
        return items == null || items.isEmpty();
    }

    private static boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(20, score));
    }

    private static int clampOverall(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
