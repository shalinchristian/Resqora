package com.resqora.resqora_backend.model.resume;

import jakarta.validation.constraints.Size;

// size limits only kick in where this is validated (builder export body), the parser doesn't care
public record ProjectEntry(@Size(max = 200) String name, @Size(max = 4000) String description) {
}
