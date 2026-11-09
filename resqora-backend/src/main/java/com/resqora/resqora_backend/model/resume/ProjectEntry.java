package com.resqora.resqora_backend.model.resume;

import jakarta.validation.constraints.Size;

// Size limits only apply where the record is validated (the builder export body); the parser is unaffected.
public record ProjectEntry(@Size(max = 200) String name, @Size(max = 4000) String description) {
}
