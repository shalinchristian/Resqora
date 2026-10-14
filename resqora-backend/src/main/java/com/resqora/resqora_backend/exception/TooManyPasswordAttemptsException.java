package com.resqora.resqora_backend.exception;

// 429 after too many wrong passwords on account deletion; retryAfterSeconds goes into Retry-After
public class TooManyPasswordAttemptsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyPasswordAttemptsException(long retryAfterSeconds, long windowMinutes) {
        super("Too many wrong passwords. Try again in " + windowMinutes + " minute" + (windowMinutes == 1 ? "" : "s") + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
