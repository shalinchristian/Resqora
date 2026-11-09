package com.resqora.resqora_backend.exception;

// 429; retryAfterSeconds = seconds until the daily reset (midnight in resqora.timezone).
// code tells the frontend which daily limit was hit.
public class AiDailyLimitExceededException extends RuntimeException {
    // new summaries (safety limit); also the code every AI 429 had before the chat limits were split
    public static final String CODE = "AI_DAILY_LIMIT";
    public static final String RESUME_CHAT_CODE = "RESUME_CHAT_LIMIT";
    public static final String DASHBOARD_CHAT_CODE = "DASHBOARD_CHAT_LIMIT";

    private final long retryAfterSeconds;
    private final String code;

    private AiDailyLimitExceededException(String message, String code, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static AiDailyLimitExceededException summaries(long retryAfterSeconds) {
        return new AiDailyLimitExceededException("Daily AI limit reached", CODE, retryAfterSeconds);
    }

    public static AiDailyLimitExceededException resumeChat(int limit, long retryAfterSeconds) {
        return new AiDailyLimitExceededException("You've used your " + limit
                + " resume chat messages for today. They reset at midnight.", RESUME_CHAT_CODE, retryAfterSeconds);
    }

    public static AiDailyLimitExceededException dashboardChat(int limit, long retryAfterSeconds) {
        return new AiDailyLimitExceededException("You've used your " + limit
                + " dashboard chat messages for today. They reset at midnight.", DASHBOARD_CHAT_CODE,
                retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }
}
