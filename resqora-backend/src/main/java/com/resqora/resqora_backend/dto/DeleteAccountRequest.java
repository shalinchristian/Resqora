package com.resqora.resqora_backend.dto;

import jakarta.validation.constraints.NotBlank;

// the password is asked again so a stolen or forgotten-open session can't wipe the account
public record DeleteAccountRequest(@NotBlank String password) {
}
