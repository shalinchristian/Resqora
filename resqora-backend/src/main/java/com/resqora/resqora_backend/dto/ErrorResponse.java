package com.resqora.resqora_backend.dto;

public record ErrorResponse(int status, String error, String message) {

}