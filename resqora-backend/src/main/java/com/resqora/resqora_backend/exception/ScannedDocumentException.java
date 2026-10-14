package com.resqora.resqora_backend.exception;

// The upload has (almost) no extractable text, e.g. a scanned PDF. Mapped to 422; nothing is saved.
public class ScannedDocumentException extends RuntimeException {
    public ScannedDocumentException(String message) {
        super(message);
    }
}
