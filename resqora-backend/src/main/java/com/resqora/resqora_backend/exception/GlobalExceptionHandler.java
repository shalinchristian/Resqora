package com.resqora.resqora_backend.exception;

import com.resqora.resqora_backend.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(IllegalArgumentException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        return errorResponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    // @Valid failed on a request body, e.g. blank email or short password
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return errorResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + message);
    }

    // Body is not valid JSON (or missing)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException exception) {
        // valid JSON but an unknown enum value, e.g. {"action":"FOO"}: name the allowed values
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                    && invalid.getTargetType().isEnum()) {
                String allowed = java.util.Arrays.stream(invalid.getTargetType().getEnumConstants())
                        .map(String::valueOf).collect(Collectors.joining(", "));
                String field = invalid.getPath().isEmpty() ? "value" : invalid.getPath().get(invalid.getPath().size() - 1).getPropertyName();
                return errorResponse(HttpStatus.BAD_REQUEST,
                        "Invalid " + field + " '" + invalid.getValue() + "'. Allowed values: " + allowed);
            }
        }
        return errorResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request");
    }

    // 403, not 401: the token is fine, only the typed password is wrong, so the frontend must not log out
    @ExceptionHandler(IncorrectPasswordException.class)
    public ResponseEntity<ErrorResponse> handleIncorrectPassword(IncorrectPasswordException exception) {
        return errorResponse(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(DemoAccountReadOnlyException.class)
    public ResponseEntity<ErrorResponse> handleDemoReadOnly(DemoAccountReadOnlyException exception) {
        return errorResponse(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticatedUserNotFound(
            AuthenticatedUserNotFoundException exception) {
        // token was valid but its user no longer exists: the caller is not authenticated
        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyLoginAttempts(TooManyLoginAttemptsException exception) {
        ErrorResponse body = new ErrorResponse(429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(ResumeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResumeNotFound(ResumeNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResumeLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleResumeLimitExceeded(ResumeLimitExceededException exception) {
        return errorResponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    // No API key, app-wide daily cap reached, or Gemini failed (quota 429, timeout, 5xx, bad reply)
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException exception) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(AiDailyLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleAiDailyLimit(AiDailyLimitExceededException exception) {
        return tooManyRequests(exception.getMessage(), exception.getRetryAfterSeconds());
    }

    @ExceptionHandler(TooManyPasswordAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyPasswordAttempts(TooManyPasswordAttemptsException exception) {
        return tooManyRequests(exception.getMessage(), exception.getRetryAfterSeconds());
    }

    private static ResponseEntity<ErrorResponse> tooManyRequests(String message, long retryAfterSeconds) {
        ErrorResponse body = new ErrorResponse(429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), message);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(body);
    }

    @ExceptionHandler(JobSuggestionsUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleJobSuggestionsUnavailable(JobSuggestionsUnavailableException exception) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingFile(MissingServletRequestPartException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Resume file is required");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception) {
        return errorResponse(HttpStatus.CONTENT_TOO_LARGE, "File size must not exceed 2 MB");
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(FileTooLargeException exception) {
        return errorResponse(HttpStatus.CONTENT_TOO_LARGE, exception.getMessage());
    }

    // Upload sent as JSON / not multipart, or a broken multipart body
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Upload must be multipart/form-data with a 'file' part");
    }

    @ExceptionHandler(ScannedDocumentException.class)
    public ResponseEntity<ErrorResponse> handleScanned(ScannedDocumentException exception) {
        return errorResponse(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    // A unique constraint lost a race (e.g. two registrations with the same email at the same moment)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
        return errorResponse(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        // the client refused JSON, so no body can be written
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    // Catch-all: anything unexpected is a 500 in our JSON shape (never forwarded to /error -> 401).
    // Spring MVC exceptions that carry their own status (e.g. missing request parameter) keep it.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse withStatus) {
            HttpStatus status = HttpStatus.resolve(withStatus.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return errorResponse(status, status.getReasonPhrase());
            }
        }
        log.error("Unhandled exception", exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ErrorResponse> handleDocumentExtraction(DocumentExtractionException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // Audit (slice 11): these used to fall through to Spring's default body, not our ErrorResponse format
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method " + exception.getMethod() + " is not allowed here");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, "Endpoint not found");
    }

    // e.g. /api/resumes/abc where a number is expected
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid value for " + exception.getName());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException exception) {
        return errorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type");
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message) {
        ErrorResponse response = new ErrorResponse(status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(response);
    }
}