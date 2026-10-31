package com.resqora.resqora_backend.exception;

// 403 at login: the password was right but the email isn't verified yet (only sent after the password check)
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email not verified");
    }
}
