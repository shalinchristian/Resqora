package com.resqora.resqora_backend.exception;

public class JobSuggestionsUnavailableException extends RuntimeException {
    public JobSuggestionsUnavailableException() {
        super("Job suggestions not available right now");
    }

    public JobSuggestionsUnavailableException(Throwable cause) {
        super("Job suggestions not available right now", cause);
    }
}
