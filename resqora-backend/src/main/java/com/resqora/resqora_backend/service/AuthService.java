package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.audit.LoginAudit;
import com.resqora.resqora_backend.audit.LoginAudit.Reason;
import com.resqora.resqora_backend.dto.ChangePasswordRequest;
import com.resqora.resqora_backend.dto.LoginRequest;
import com.resqora.resqora_backend.dto.LoginResponse;
import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.dto.UpdateMeRequest;
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
import com.resqora.resqora_backend.repository.ChatHistoryRepository;
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
    private final ChatHistoryRepository chatHistoryRepository;
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
            ChatHistoryRepository chatHistoryRepository,
            PasswordAttemptLimiter passwordAttemptLimiter,
            EmailCodeService emailCodeService,
            CodeRequestLimiter codeRequestLimiter,
            @Value("${resqora.email.require-verification:true}") boolean requireEmailVerification) {
        this.emailCodeService = emailCodeService;
        this.codeRequestLimiter = codeRequestLimiter;
        this.requireEmailVerification = requireEmailVerification;
        this.passwordAttemptLimiter = passwordAttemptLimiter;
        this.resumeRepository = resumeRepository;
        this.chatHistoryRepository = chatHistoryRepository;
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
        User user = userRepository.findByEmail(request.email()).orElse(null);
        LoginAudit.identify(request.email(), user == null ? null : user.getId());
        if (user == null) {
            LoginAudit.failure(Reason.USER_NOT_FOUND);
            throw new InvalidCodeException();
        }
        if (user.isEmailVerified()) {
            LoginAudit.failure(Reason.EMAIL_ALREADY_VERIFIED);
            throw new InvalidCodeException();
        }
        try {
            emailCodeService.consume(user.getId(), Purpose.VERIFY_EMAIL, request.code());
        } catch (InvalidCodeException exception) {
            LoginAudit.failure(Reason.INVALID_CODE);
            throw exception;
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        LoginResponse response = new LoginResponse(jwtService.generateToken(user), user.getName());
        LoginAudit.authenticated();
        return response;
    }

    // Always ends the same way (202) whether or not the email exists, so it can't be used to find accounts.
    @Transactional
    public void resendVerification(String email, String clientIp) {
        rejectIfTooManyCodeRequests(clientIp);
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> emailCodeService.issue(user, Purpose.VERIFY_EMAIL));
    }

    // Same rule: always 202, whether or not the email exists.
    @Transactional
    public void forgotPassword(String email, String clientIp) {
        rejectIfTooManyCodeRequests(clientIp);
        userRepository.findByEmail(email)
                .ifPresent(user -> emailCodeService.issue(user, Purpose.RESET_PASSWORD));
    }

    @Transactional(noRollbackFor = InvalidCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCodeException::new);
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
        LoginAudit.identify(request.email(), null);
        // checked before the password, so a blocked attacker learns nothing more
        if (loginAttemptLimiter.isBlocked(request.email(), clientIp)) {
            LoginAudit.failure(Reason.RATE_LIMITED);
            throw new TooManyLoginAttemptsException(loginAttemptLimiter.retryAfterSeconds(request.email(), clientIp));
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        LoginAudit.identify(request.email(), user == null ? null : user.getId());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LoginAudit.failure(user == null ? Reason.USER_NOT_FOUND : Reason.BAD_CREDENTIALS);
            loginAttemptLimiter.recordFailure(request.email(), clientIp);
            throw new InvalidCredentialsException();
        }

        // checked only after the password is right, so it reveals nothing to someone guessing
        if (requireEmailVerification && !user.isEmailVerified()) {
            LoginAudit.failure(Reason.EMAIL_NOT_VERIFIED);
            throw new EmailNotVerifiedException();
        }
        loginAttemptLimiter.recordSuccess(request.email());
        // name is null for older accounts; the frontend decides what to show
        LoginResponse response = new LoginResponse(jwtService.generateToken(user), user.getName());
        LoginAudit.authenticated();
        return response;
    }

    public MeResponse me(Long userId) {
        return toMe(findUser(userId));
    }

    // Only the fields that were sent are changed.
    @Transactional
    public MeResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = findUser(userId);
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.aiEnabled() != null) {
            user.setAiEnabled(request.aiEnabled());
        }
        return toMe(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
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

    // Soft delete. The account row stays as a record with the name, email and sign-up time, marked
    // DELETED with the time, and can never log in again; the same email can sign up as a new account.
    // Everything the user made is removed: resumes (analyses, AI summaries, AI answers and job
    // suggestions and resume chat history cascade from them; files are stored in resume rows),
    // all chat history (including dashboard), and any pending email codes.
    // ai_usage rows stay: they only record that a call happened, and the app-wide daily AI caps count them.
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findByIdForUpdate(userId).filter(u -> !u.isDeleted())
                .orElseThrow(AuthenticatedUserNotFoundException::new);
        // brute-force protection: 5 wrong passwords per user in 15 min -> 429 (checked before the hash)
        passwordAttemptLimiter.rejectIfBlocked(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            passwordAttemptLimiter.recordFailure(userId);
            throw new IncorrectPasswordException("Password is incorrect");
        }
        passwordAttemptLimiter.recordSuccess(userId);
        chatHistoryRepository.deleteByUserId(userId);
        resumeRepository.deleteAll(resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId));
        resumeRepository.flush();
        emailCodeService.discardAll(userId);
        user.markDeleted(java.time.Instant.now());
        userRepository.saveAndFlush(user);
    }

    // valid token but the account is gone or deleted: treat as not authenticated -> 401
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private static MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.isAiEnabled());
    }
}
