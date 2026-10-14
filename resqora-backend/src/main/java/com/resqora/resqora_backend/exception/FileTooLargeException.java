package com.resqora.resqora_backend.exception;

// Mapped to 413 Payload Too Large.
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("File size must not exceed 5 MB");
    }
}
