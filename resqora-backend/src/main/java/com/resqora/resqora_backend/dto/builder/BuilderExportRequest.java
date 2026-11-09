package com.resqora.resqora_backend.dto.builder;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// Builder form payload. Same field names as ParsedResume (so GET /api/resumes/{id}/parsed can prefill it),
// plus templateId, an optional summary/headline/links/highlights/achievements/languages and the output format.
// Only what is sent here is written to the file.
public record BuilderExportRequest(
        @NotBlank
        @Pattern(regexp = "classic|combination|campus|centered|header-band",
                message = "must be one of classic, combination, campus, centered, header-band")
        String templateId,
        @NotBlank @Size(max = 200) String name,
        // optional line under the name, e.g. "Backend Developer | Java, Spring Boot"
        @Size(max = 200) String headline,
        @Size(max = 320) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String location,
        // printed exactly as typed (URL or handle) on the contact line
        @Size(max = 500) String linkedin,
        @Size(max = 500) String github,
        @Size(max = 2000) String summary,
        @Size(max = 100) List<@Size(max = 200) String> skills,
        @Size(max = 30) List<@Valid ExperienceEntry> experience,
        @Size(max = 20) List<@Valid EducationEntry> education,
        @Size(max = 30) List<@Valid ProjectEntry> projects,
        @Size(max = 30) List<@Size(max = 200) String> certifications,
        // file type to return; missing means docx
        @Pattern(regexp = "docx|pdf", message = "must be docx or pdf") String format,
        // bullets after Summary (after Skills on Combination)
        @Size(max = 20) List<@Size(max = 200) String> highlights,
        // bullets after Certifications
        @Size(max = 20) List<@Size(max = 200) String> achievements,
        // one comma-separated line, last section on every template
        @Size(max = 20) List<@Size(max = 200) String> languages) {

    public BuilderExportRequest {
        skills = skills == null ? List.of() : skills;
        experience = experience == null ? List.of() : experience;
        education = education == null ? List.of() : education;
        projects = projects == null ? List.of() : projects;
        certifications = certifications == null ? List.of() : certifications;
        highlights = highlights == null ? List.of() : highlights;
        achievements = achievements == null ? List.of() : achievements;
        languages = languages == null ? List.of() : languages;
    }

    // same form without an explicit format or the extra sections: DOCX
    public BuilderExportRequest(String templateId, String name, String headline, String email, String phone,
            String location, String linkedin, String github, String summary, List<String> skills,
            List<ExperienceEntry> experience, List<EducationEntry> education, List<ProjectEntry> projects,
            List<String> certifications) {
        this(templateId, name, headline, email, phone, location, linkedin, github, summary, skills, experience,
                education, projects, certifications, null);
    }

    public BuilderExportRequest(String templateId, String name, String headline, String email, String phone,
            String location, String linkedin, String github, String summary, List<String> skills,
            List<ExperienceEntry> experience, List<EducationEntry> education, List<ProjectEntry> projects,
            List<String> certifications, String format) {
        this(templateId, name, headline, email, phone, location, linkedin, github, summary, skills, experience,
                education, projects, certifications, format, null, null, null);
    }

    public boolean wantsPdf() {
        return "pdf".equals(format);
    }
}
