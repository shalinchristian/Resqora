package com.resqora.resqora_backend.model.analysis;

/**
 * One piece of feedback.
 * category = the score it belongs to (Completeness, Skills, Projects, Experience, Education, Layout);
 * type = WARNING (something to fix) or INFO (status only);
 * message = what we found; line = the resume line it is about (the evidence), or null for a whole section;
 * section = the resume section to edit (Contact, Skills, Projects, Experience, Education), or null;
 * fix = one concrete action that fixes it, or null for INFO findings.
 * Older saved findings have no section/fix; they are recomputed (see ResumeAnalyzer.VERSION).
 */
public record AnalysisFinding(String category, String type, String message, String line, String section,
        String fix) {
    public AnalysisFinding(String category, String type, String message) {
        this(category, type, message, null, null, null);
    }

    public AnalysisFinding(String category, String type, String message, String line) {
        this(category, type, message, line, null, null);
    }
}
