package com.resqora.resqora_backend.model.analysis;

/**
 * one piece of feedback on a resume.
 * category: which score it belongs to (Completeness, Skills, Projects, Experience, Education, Layout)
 * type: WARNING (something to fix) or INFO (just a status line)
 * message: what we found. line: the resume line it's about, or null for a whole section
 * section: which resume section to edit (Contact, Skills, Projects, Experience, Education), or null
 * fix: one concrete thing to do about it, null for INFO
 * older saved findings don't have section/fix, they get recomputed (see ResumeAnalyzer.VERSION)
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
