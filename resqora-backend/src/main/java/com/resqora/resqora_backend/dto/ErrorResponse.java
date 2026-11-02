package com.resqora.resqora_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// error is the http reason ("Forbidden"), code is an optional reason the frontend can check ("AI_DISABLED").
// code is left out of the json when null so older responses look the same as before
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, String code) {

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
