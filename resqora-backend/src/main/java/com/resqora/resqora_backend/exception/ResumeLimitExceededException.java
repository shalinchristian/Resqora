package com.resqora.resqora_backend.exception;

public class ResumeLimitExceededException extends RuntimeException {
    public ResumeLimitExceededException(int limit) {
        super("Resume limit reached (" + limit + ")");
    }
}
