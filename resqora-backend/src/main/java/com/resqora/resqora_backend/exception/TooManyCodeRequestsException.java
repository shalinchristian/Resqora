package com.resqora.resqora_backend.exception;

// 429 when one IP asks for too many codes; retryAfterSeconds goes into the Retry-After header
public class TooManyCodeRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyCodeRequestsException(long retryAfterSeconds) {
        super("Too many code requests, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
