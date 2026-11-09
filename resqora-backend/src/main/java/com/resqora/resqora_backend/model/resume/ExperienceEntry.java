package com.resqora.resqora_backend.model.resume;

import jakarta.validation.constraints.Size;

// Size limits only apply where the record is validated (the builder export body); the parser is unaffected.
public record ExperienceEntry(
        @Size(max = 200) String company,
        @Size(max = 200) String title,
        @Size(max = 200) String dates,
        @Size(max = 4000) String description) {
}
