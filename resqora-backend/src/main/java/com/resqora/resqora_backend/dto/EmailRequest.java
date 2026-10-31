package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// body of resend-code and forgot-password
public record EmailRequest(@NotBlank @Email String email) {
    public EmailRequest {
        email = RegistrationRequest.normalizeEmail(email);
    }
}
