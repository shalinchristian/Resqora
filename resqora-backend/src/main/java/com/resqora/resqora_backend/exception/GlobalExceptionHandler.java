package com.resqora.resqora_backend.exception;

import com.resqora.resqora_backend.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(IllegalArgumentException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingFile(MissingServletRequestPartException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Resume file is required");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "File size must not exceed 5 MB");
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ErrorResponse> handleDocumentExtraction(DocumentExtractionException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message) {
        ErrorResponse response = new ErrorResponse(status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(response);
    }
}