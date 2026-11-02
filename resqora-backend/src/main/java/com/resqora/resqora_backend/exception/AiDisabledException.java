package com.resqora.resqora_backend.exception;

// 403 AI_DISABLED: the user turned AI off in settings, so nothing is sent to any AI provider
public class AiDisabledException extends RuntimeException {
    public static final String CODE = "AI_DISABLED";

    public AiDisabledException() {
        super("AI features are turned off in your settings");
    }
}
