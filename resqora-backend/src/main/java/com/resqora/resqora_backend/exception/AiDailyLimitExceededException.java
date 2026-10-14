package com.resqora.resqora_backend.exception;

// 429; retryAfterSeconds = seconds until the daily reset (midnight in resqora.timezone)
public class AiDailyLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public AiDailyLimitExceededException(long retryAfterSeconds) {
        super("Daily AI limit reached");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
