package com.resqora.resqora_backend.exception;

// 429 on login; retryAfterSeconds goes into the Retry-After header
public class TooManyLoginAttemptsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException() {
        this(900);
    }

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many login attempts, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
