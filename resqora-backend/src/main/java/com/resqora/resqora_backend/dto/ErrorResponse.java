package com.resqora.resqora_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// error = HTTP reason phrase ("Forbidden"); code = optional machine-readable reason ("AI_DISABLED"),
// left out of the JSON when there is none, so older responses keep exactly the same shape.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, String code) {

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
