package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.exception.EmailAlreadyExistsException;
import com.resqora.resqora_backend.exception.InvalidCredentialsException;
import com.resqora.resqora_backend.exception.TooManyLoginAttemptsException;
import com.resqora.resqora_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    public User register(RegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.email(), passwordHash, request.name());
        try {
            // flush now so a parallel duplicate hits the unique constraint here, not after the response
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        // checked before the password, so a blocked attacker learns nothing more
        if (loginAttemptLimiter.isBlocked(request.email(), clientIp)) {
            throw new TooManyLoginAttemptsException();
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(request.email(), clientIp);
            throw new InvalidCredentialsException();
        }

        loginAttemptLimiter.recordSuccess(request.email());
        // name is null for older accounts; the frontend decides what to show
        return new LoginResponse(jwtService.generateToken(user), user.getName());
    }

    public MeResponse me(Long userId) {
        return toMe(findUser(userId));
    }

    @Transactional
    public MeResponse updateName(Long userId, String name) {
        User user = findUser(userId);
        user.setName(name);
        return toMe(userRepository.save(user));
    }

    // valid token but the user row is gone (e.g. deleted): treat as not authenticated -> 401
    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private static MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName());
    }
}
