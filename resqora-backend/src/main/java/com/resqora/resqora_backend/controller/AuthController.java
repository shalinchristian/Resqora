package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.dto.RegisterResponse;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegistrationRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getEmail(), user.getDisplayName()));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getDisplayName())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}