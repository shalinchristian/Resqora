package com.resqora.resqora_backend.dto.builder;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Field paths ("experience[3].dates") and the names users see for them ("Experience 4 dates"),
// shared by validation messages and the PDF "can't show this text" error.
public final class BuilderFields {
    private static final Pattern INDEXED = Pattern.compile("^(\\w+)\\[(\\d+)]\\.?(\\w*)");
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("templateId", "Template"), Map.entry("name", "Name"), Map.entry("headline", "Headline"),
            Map.entry("email", "Email"), Map.entry("phone", "Phone"), Map.entry("location", "Location"),
            Map.entry("linkedin", "LinkedIn"), Map.entry("github", "GitHub"), Map.entry("summary", "Summary"),
            Map.entry("skills", "Skills"), Map.entry("experience", "Experience"),
            Map.entry("education", "Education"), Map.entry("projects", "Projects"),
            Map.entry("certifications", "Certifications"), Map.entry("highlights", "Highlights"),
            Map.entry("achievements", "Achievements"), Map.entry("languages", "Languages"),
            Map.entry("format", "Format"), Map.entry("institution", "school"), Map.entry("details", "details"));

    private BuilderFields() {
    }

    // "experience[3].dates" -> "Experience 4 dates", "skills[0]" -> "Skills item 1", "summary" -> "Summary"
    public static String label(String path) {
        Matcher indexed = INDEXED.matcher(path);
        if (indexed.find()) {
            String list = NAMES.getOrDefault(indexed.group(1), indexed.group(1));
            int number = Integer.parseInt(indexed.group(2)) + 1;
            String field = indexed.group(3);
            return field.isEmpty() ? list + " item " + number
                    : list + " " + number + " " + NAMES.getOrDefault(field, field);
        }
        return NAMES.getOrDefault(path, path);
    }

    // every text value in the request, keyed by its path, in form order
    public static Map<String, String> texts(BuilderExportRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        put(out, "name", request.name());
        put(out, "headline", request.headline());
        put(out, "email", request.email());
        put(out, "phone", request.phone());
        put(out, "location", request.location());
        put(out, "linkedin", request.linkedin());
        put(out, "github", request.github());
        put(out, "summary", request.summary());
        list(out, "highlights", request.highlights());
        list(out, "skills", request.skills());
        List<ExperienceEntry> experience = request.experience();
        for (int i = 0; i < experience.size(); i++) {
            ExperienceEntry e = experience.get(i);
            if (e != null) {
                put(out, "experience[" + i + "].title", e.title());
                put(out, "experience[" + i + "].company", e.company());
                put(out, "experience[" + i + "].dates", e.dates());
                put(out, "experience[" + i + "].description", e.description());
            }
        }
        List<EducationEntry> education = request.education();
        for (int i = 0; i < education.size(); i++) {
            EducationEntry e = education.get(i);
            if (e != null) {
                put(out, "education[" + i + "].institution", e.institution());
                put(out, "education[" + i + "].degree", e.degree());
                put(out, "education[" + i + "].dates", e.dates());
                put(out, "education[" + i + "].location", e.location());
                put(out, "education[" + i + "].details", e.details());
            }
        }
        List<ProjectEntry> projects = request.projects();
        for (int i = 0; i < projects.size(); i++) {
            ProjectEntry p = projects.get(i);
            if (p != null) {
                put(out, "projects[" + i + "].name", p.name());
                put(out, "projects[" + i + "].description", p.description());
            }
        }
        list(out, "certifications", request.certifications());
        list(out, "achievements", request.achievements());
        list(out, "languages", request.languages());
        return out;
    }

    private static void list(Map<String, String> out, String name, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            put(out, name + "[" + i + "]", values.get(i));
        }
    }

    private static void put(Map<String, String> out, String path, String value) {
        if (value != null && !value.isBlank()) {
            out.put(path, value);
        }
    }
}
