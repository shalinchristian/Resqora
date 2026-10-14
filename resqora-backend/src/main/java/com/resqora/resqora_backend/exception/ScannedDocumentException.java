package com.resqora.resqora_backend.exception;

// upload has almost no text to extract, like a scanned PDF. 422 and nothing is saved
public class ScannedDocumentException extends RuntimeException {
    public ScannedDocumentException(String message) {
        super(message);
    }
}
