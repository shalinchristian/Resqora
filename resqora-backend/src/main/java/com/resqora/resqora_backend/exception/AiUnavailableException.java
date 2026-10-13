package com.resqora.resqora_backend.exception;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("AI service not available right now");
    }

    public AiUnavailableException(Throwable cause) {
        super("AI service not available right now", cause);
    }
}
