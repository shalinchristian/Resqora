package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.EmailAlreadyExistsException;
import com.resqora.resqora_backend.exception.InvalidCredentialsException;
import com.resqora.resqora_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, request.name());
        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new LoginResponse(jwtService.generateToken(user), user.getDisplayName());
    }
}