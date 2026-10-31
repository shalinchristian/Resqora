package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.demo.DemoAccountGuard;
import com.resqora.resqora_backend.dto.ChangePasswordRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.dto.RegistrationRequest;
import com.resqora.resqora_backend.dto.ResetPasswordRequest;
import com.resqora.resqora_backend.dto.VerifyEmailRequest;
import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.EmailNotVerifiedException;
import com.resqora.resqora_backend.exception.InvalidCodeException;
import com.resqora.resqora_backend.exception.TooManyCodeRequestsException;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.exception.EmailAlreadyExistsException;
import com.resqora.resqora_backend.exception.IncorrectPasswordException;
import com.resqora.resqora_backend.exception.InvalidCredentialsException;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.exception.TooManyLoginAttemptsException;
import com.resqora.resqora_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private final ResumeRepository resumeRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final PasswordAttemptLimiter passwordAttemptLimiter;
    private final EmailCodeService emailCodeService;
    private final CodeRequestLimiter codeRequestLimiter;
    // true in production; tests that are not about verification turn it off to keep register -> login simple
    private final boolean requireEmailVerification;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptLimiter loginAttemptLimiter,
            ResumeRepository resumeRepository,
            DemoAccountGuard demoAccountGuard,
            PasswordAttemptLimiter passwordAttemptLimiter,
            EmailCodeService emailCodeService,
            CodeRequestLimiter codeRequestLimiter,
            @Value("${resqora.email.require-verification:true}") boolean requireEmailVerification) {
        this.emailCodeService = emailCodeService;
        this.codeRequestLimiter = codeRequestLimiter;
        this.requireEmailVerification = requireEmailVerification;
        this.passwordAttemptLimiter = passwordAttemptLimiter;
        this.resumeRepository = resumeRepository;
        this.demoAccountGuard = demoAccountGuard;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @Transactional
    public User register(RegistrationRequest request, String clientIp) {
        if (requireEmailVerification) {
            rejectIfTooManyCodeRequests(clientIp); // each sign-up sends an email
        }
        String passwordHash = passwordEncoder.encode(request.password());
        User existing = userRepository.findByEmail(request.email()).orElse(null);
        if (existing != null && (existing.isEmailVerified() || !requireEmailVerification)) {
            throw new EmailAlreadyExistsException();
        }

        User user;
        if (existing != null) {
            // never verified: whoever signed up can't log in anyway, so let the real owner sign up again
            // (fixes typo'd passwords and stops someone "reserving" another person's email)
            existing.setPasswordHash(passwordHash);
            existing.setName(request.name());
            user = userRepository.saveAndFlush(existing);
        } else {
            user = new User(request.email(), passwordHash, request.name());
            user.setEmailVerified(!requireEmailVerification);
            try {
                // flush now so a parallel duplicate hits the unique constraint here, not after the response
                user = userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException exception) {
                throw new EmailAlreadyExistsException();
            }
        }
        if (!user.isEmailVerified()) {
            emailCodeService.issue(user, Purpose.VERIFY_EMAIL);
        }
        return user;
    }

    // 6-digit code from the sign-up email -> account verified and logged in straight away
    @Transactional(noRollbackFor = InvalidCodeException.class)
    public LoginResponse verifyEmail(VerifyEmailRequest request) {
        // unknown email gets the same answer as a wrong code
        User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCodeException::new);
        if (user.isEmailVerified()) {
            throw new InvalidCodeException();
        }
        emailCodeService.consume(user.getId(), Purpose.VERIFY_EMAIL, request.code());
        user.setEmailVerified(true);
        userRepository.save(user);
        return new LoginResponse(jwtService.generateToken(user), user.getName());
    }

    // Always ends the same way (202) whether or not the email exists, so it can't be used to find accounts.
    @Transactional
    public void resendVerification(String email, String clientIp) {
        rejectIfTooManyCodeRequests(clientIp);
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> emailCodeService.issue(user, Purpose.VERIFY_EMAIL));
    }

    // Same rule: always 202. The demo account is shared, so its password can't be reset.
    @Transactional
    public void forgotPassword(String email, String clientIp) {
        rejectIfTooManyCodeRequests(clientIp);
        userRepository.findByEmail(email)
                .filter(user -> !demoAccountGuard.isDemo(user))
                .ifPresent(user -> emailCodeService.issue(user, Purpose.RESET_PASSWORD));
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCodeException::new);
        if (demoAccountGuard.isDemo(user)) {
            throw new InvalidCodeException();
        }
        emailCodeService.consume(user.getId(), Purpose.RESET_PASSWORD, request.code());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // the code reached their inbox, which proves they own the email
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            emailCodeService.discard(user.getId(), Purpose.VERIFY_EMAIL);
        }
        userRepository.save(user);
        // a reset should also lift a login lock-out from earlier wrong passwords
        loginAttemptLimiter.recordSuccess(user.getEmail());
    }

    private void rejectIfTooManyCodeRequests(String clientIp) {
        long wait = codeRequestLimiter.tryAcquire(clientIp);
        if (wait > 0) {
            throw new TooManyCodeRequestsException(wait);
        }
    }

    public LoginResponse login(LoginRequest request, String clientIp) {
        // checked before the password, so a blocked attacker learns nothing more
        if (loginAttemptLimiter.isBlocked(request.email(), clientIp)) {
            throw new TooManyLoginAttemptsException(loginAttemptLimiter.retryAfterSeconds(request.email(), clientIp));
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(request.email(), clientIp);
            throw new InvalidCredentialsException();
        }

        // checked only after the password is right, so it reveals nothing to someone guessing
        if (requireEmailVerification && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
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
        demoAccountGuard.rejectIfDemo(user);
        user.setName(name);
        return toMe(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        demoAccountGuard.rejectIfDemo(user);
        passwordAttemptLimiter.rejectIfBlocked(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            passwordAttemptLimiter.recordFailure(userId);
            throw new IncorrectPasswordException();
        }
        passwordAttemptLimiter.recordSuccess(userId);
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // Removes the user and everything they own. Resumes are deleted explicitly (users -> resumes has no
    // ON DELETE CASCADE); analyses and job suggestions cascade from resumes, ai_usage from users.
    // Stored files and summaries live in the resumes rows (no files on disk), so they go with them.
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = findUser(userId);
        demoAccountGuard.rejectIfDemo(user);
        // brute-force protection: 5 wrong passwords per user in 15 min -> 429 (checked before the hash)
        passwordAttemptLimiter.rejectIfBlocked(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            passwordAttemptLimiter.recordFailure(userId);
            throw new IncorrectPasswordException("Password is incorrect");
        }
        passwordAttemptLimiter.recordSuccess(userId);
        resumeRepository.deleteAll(resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId));
        resumeRepository.flush();
        userRepository.delete(user);
        userRepository.flush();
    }

    // valid token but the user row is gone (e.g. deleted): treat as not authenticated -> 401
    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private static MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName());
    }
}
