package com.resqora.resqora_backend.model.resume;

// location: the right-aligned "City, State" next to the institution name, when the resume has one.
public record EducationEntry(String institution, String degree, String dates, String details, String location) {

    public EducationEntry(String institution, String degree, String dates, String details) {
        this(institution, degree, dates, details, null);
    }
}
