package com.resqora.resqora_backend.model.analysis;

/**
 * One piece of feedback. {@code line} is the resume line it is about (e.g. "Paytm | Backend Engineer"),
 * or null for section-level feedback.
 */
public record AnalysisFinding(String category, String type, String message, String line) {
    public AnalysisFinding(String category, String type, String message) {
        this(category, type, message, null);
    }
}
