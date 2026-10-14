package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.NotBlank;

// ask for the password again so someone on a stolen or left-open session can't delete the account
public record DeleteAccountRequest(@NotBlank String password) {
}
