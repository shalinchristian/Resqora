package com.resqora.resqora_backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerSlice12Test {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionIs500WithStandardBody() {
        var response = handler.handleUnexpected(new NullPointerException("boom"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Unexpected error", response.getBody().message());
    }

    @Test
    void oversizedUploadIs413() {
        assertEquals(413, handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1)).getStatusCode().value());
    }

    @Test
    void constraintRaceIs409() {
        assertEquals(409, handler.handleDataIntegrity(new DataIntegrityViolationException("dup")).getStatusCode().value());
    }
}
