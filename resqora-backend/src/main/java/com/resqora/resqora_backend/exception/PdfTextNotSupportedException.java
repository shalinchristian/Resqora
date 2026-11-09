package com.resqora.resqora_backend.exception;

import java.util.Locale;

// The PDF fonts can't draw (or ATS parsers couldn't read back) some character the user typed.
// 422 with a code so the frontend can offer the DOCX download instead.
public class PdfTextNotSupportedException extends RuntimeException {
    public static final String CODE = "PDF_UNSUPPORTED_TEXT";

    public PdfTextNotSupportedException(String field, String value, int codePoint) {
        super(message(field, value, codePoint));
    }

    private static String message(String field, String value, int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        String what = switch (script) {
            case COMMON, INHERITED, UNKNOWN -> "the character \"" + new String(Character.toChars(codePoint)) + "\"";
            case DEVANAGARI -> "Devanagari (Hindi/Marathi) text";
            default -> {
                String name = script.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1) + " text";
            }
        };
        String shown = value.strip();
        if (shown.codePointCount(0, shown.length()) > 40) {
            shown = shown.substring(0, shown.offsetByCodePoints(0, 40)) + "…";
        }
        return "PDF export can't show " + what + " yet (" + field + ": \"" + shown
                + "\"). Download DOCX instead, or type that part in English.";
    }
}
