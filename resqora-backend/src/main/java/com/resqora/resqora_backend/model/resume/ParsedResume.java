package com.resqora.resqora_backend.model.resume;

import java.util.List;

public record ParsedResume(
        String name,
        String email,
        String phone,
        String location,
        List<String> skills,
        List<EducationEntry> education,
        List<ExperienceEntry> experience,
        List<ProjectEntry> projects,
        List<String> certifications) {

    public ParsedResume {
        skills = skills == null ? List.of() : List.copyOf(skills);
        education = education == null ? List.of() : List.copyOf(education);
        experience = experience == null ? List.of() : List.copyOf(experience);
        projects = projects == null ? List.of() : List.copyOf(projects);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
    }
}