package com.resqora.resqora_backend.exception;

public class AiUnavailableException extends RuntimeException {
    public static final String CODE = "AI_UNAVAILABLE";

    public AiUnavailableException() {
        super("AI service not available right now");
    }

    public AiUnavailableException(Throwable cause) {
        super("AI service not available right now", cause);
    }
}
