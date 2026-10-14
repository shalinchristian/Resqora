package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ChangePasswordRequest;
import com.resqora.resqora_backend.dto.DeleteAccountRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.dto.RegisterResponse;
import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.dto.UpdateNameRequest;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegistrationRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getEmail(), user.getName()));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // getRemoteAddr only: X-Forwarded-For can be faked by the client
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return authService.me((Long) authentication.getPrincipal());
    }

    @PatchMapping("/me")
    public MeResponse updateMe(@Valid @RequestBody UpdateNameRequest request, Authentication authentication) {
        return authService.updateName((Long) authentication.getPrincipal(), request.name());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        authService.changePassword((Long) authentication.getPrincipal(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@Valid @RequestBody DeleteAccountRequest request, Authentication authentication) {
        authService.deleteAccount((Long) authentication.getPrincipal(), request.password());
        return ResponseEntity.noContent().build();
    }
}
