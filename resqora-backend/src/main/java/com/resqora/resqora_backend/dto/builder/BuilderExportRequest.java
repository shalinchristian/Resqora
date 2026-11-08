package com.resqora.resqora_backend.dto.builder;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// Builder form payload. Same field names as ParsedResume (so GET /api/resumes/{id}/parsed can prefill it),
// plus templateId and an optional summary. Only what is sent here is written to the file.
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
        @Size(max = 100) List<String> skills,
        @Size(max = 30) List<ExperienceEntry> experience,
        @Size(max = 20) List<EducationEntry> education,
        @Size(max = 30) List<ProjectEntry> projects,
        @Size(max = 30) List<String> certifications) {

    public BuilderExportRequest {
        skills = skills == null ? List.of() : skills;
        experience = experience == null ? List.of() : experience;
        education = education == null ? List.of() : education;
        projects = projects == null ? List.of() : projects;
        certifications = certifications == null ? List.of() : certifications;
    }
}
