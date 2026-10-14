package com.resqora.resqora_backend.exception;

// gets mapped to 413
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("File size must not exceed 5 MB");
    }
}
