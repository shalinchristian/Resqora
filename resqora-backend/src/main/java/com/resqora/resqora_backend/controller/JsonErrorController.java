package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Errors that escape the controllers (filters, container) are forwarded to /error.
// /error is public in SecurityConfig, so they now keep their real status instead of becoming 401.
@Hidden
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = code instanceof Integer value && HttpStatus.resolve(value) != null
                ? HttpStatus.valueOf(value) : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = status.is5xxServerError() ? "Unexpected error" : status.getReasonPhrase();
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
