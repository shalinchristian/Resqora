package com.resqora.resqora_backend.exception;

// 429; retryAfterSeconds = seconds until the daily reset (midnight in resqora.timezone)
public class AiDailyLimitExceededException extends RuntimeException {
    public static final String CODE = "AI_DAILY_LIMIT";
    private final long retryAfterSeconds;

    public AiDailyLimitExceededException(long retryAfterSeconds) {
        super("Daily AI limit reached");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
