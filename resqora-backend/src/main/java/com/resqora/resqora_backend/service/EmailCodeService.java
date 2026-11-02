package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.entity.EmailCode;
import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.InvalidCodeException;
import com.resqora.resqora_backend.repository.EmailCodeRepository;
import com.resqora.resqora_backend.service.email.CodeEmailTemplate;
import com.resqora.resqora_backend.service.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Creates, emails and checks the 6-digit codes used for email verification and password reset.
// Rules: one live code per user per purpose, valid for 10 minutes, 5 wrong tries, a new one at most every 60 seconds.
@Service
public class EmailCodeService {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    static final int MAX_ATTEMPTS = 5;

    private final EmailCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final CodeEmailTemplate template;
    private final Duration validFor;
    private final Duration resendCooldown;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    public EmailCodeService(
            EmailCodeRepository repository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            CodeEmailTemplate template,
            @Value("${resqora.email.code-valid-minutes:10}") long validMinutes,
            @Value("${resqora.email.resend-cooldown-seconds:60}") long cooldownSeconds) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.template = template;
        this.validFor = Duration.ofMinutes(validMinutes);
        this.resendCooldown = Duration.ofSeconds(cooldownSeconds);
    }

    // for tests: move time forward without sleeping
    void setClock(Clock clock) {
        this.clock = clock;
    }

    // Emails a fresh code, replacing any older one. Returns false (and sends nothing) inside the 60-second cooldown.
    @Transactional
    public boolean issue(User user, Purpose purpose) {
        Instant now = clock.instant();
        EmailCode existing = repository.findByUserIdAndPurpose(user.getId(), purpose).orElse(null);
        if (existing != null && now.isBefore(existing.getCreatedAt().plus(resendCooldown))) {
            return false;
        }
        if (existing != null) {
            repository.delete(existing);
            repository.flush();
        }
        // SecureRandom, not Random: codes must not be guessable from earlier ones
        String code = String.format("%06d", random.nextInt(1_000_000));
        repository.saveAndFlush(new EmailCode(user.getId(), purpose, passwordEncoder.encode(code), now, now.plus(validFor)));
        try {
            CodeEmailTemplate.CodeEmail email = template.render(purpose, code, user.getName());
            emailSender.send(user.getEmail(), email.subject(), email.text(), email.html());
        } catch (RuntimeException exception) {
            // the code is saved, so the user can press "resend" once email works again
            log.error("Could not send {} email to user {}: {}", purpose, user.getId(), exception.getMessage());
        }
        return true;
    }

    // Checks a code and deletes it when it is right, so each code works only once.
    // A wrong code counts as a try; after 5 tries the code is deleted and a new one is needed.
    @Transactional(noRollbackFor = InvalidCodeException.class)
    public void consume(Long userId, Purpose purpose, String code) {
        EmailCode saved = repository.findByUserIdAndPurpose(userId, purpose).orElseThrow(InvalidCodeException::new);
        if (!clock.instant().isBefore(saved.getExpiresAt())) {
            repository.delete(saved);
            throw new InvalidCodeException();
        }
        if (code == null || !passwordEncoder.matches(code, saved.getCodeHash())) {
            saved.recordWrongAttempt();
            if (saved.getAttempts() >= MAX_ATTEMPTS) {
                repository.delete(saved);
            } else {
                repository.save(saved);
            }
            throw new InvalidCodeException();
        }
        repository.delete(saved);
    }

    public void discard(Long userId, Purpose purpose) {
        repository.deleteByUserIdAndPurpose(userId, purpose);
    }
}
