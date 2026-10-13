package com.resqora.resqora_backend.exception;

public class AiDailyLimitExceededException extends RuntimeException {
    public AiDailyLimitExceededException() {
        super("Daily AI limit reached");
    }
}
