package com.resqora.resqora_backend.model.resume;

import jakarta.validation.constraints.Size;

// location: the right-aligned "City, State" next to the institution name, when the resume has one.
// Size limits only apply where the record is validated (the builder export body); the parser is unaffected.
public record EducationEntry(
        @Size(max = 200) String institution,
        @Size(max = 200) String degree,
        @Size(max = 200) String dates,
        @Size(max = 4000) String details,
        @Size(max = 200) String location) {

    public EducationEntry(String institution, String degree, String dates, String details) {
        this(institution, degree, dates, details, null);
    }
}
