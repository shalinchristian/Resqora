package com.resqora.resqora_backend.exception;

// 429 when one user exports or parses too often; retryAfterSeconds goes into the Retry-After header
public class TooManyBuilderRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyBuilderRequestsException(String what, long retryAfterSeconds) {
        super("Too many " + what + ". Try again in " + duration(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String duration(long seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
