package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public String register(@Valid @RequestBody RegistrationRequest request) {
        authService.register(request);
        return "Registration successful";
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest request) {
        authService.login(request);
        return "Login successful";
    }
}