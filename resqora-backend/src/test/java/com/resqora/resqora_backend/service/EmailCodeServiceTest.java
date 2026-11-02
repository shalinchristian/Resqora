package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.InvalidCodeException;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.email.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

// expiry and cooldown, using a fixed clock instead of waiting 10 minutes
@SpringBootTest
class EmailCodeServiceTest {
    @Autowired private EmailCodeService service;
    @Autowired private UserRepository userRepository;
    @MockitoBean private EmailSender emailSender;

    private final AtomicReference<String> lastCode = new AtomicReference<>();

    @AfterEach
    void resetClock() {
        service.setClock(Clock.systemUTC());
    }

    private User newUser(String email) {
        doAnswer(inv -> {
            String text = inv.getArgument(2);
            lastCode.set(text.replaceAll("(?s).*?(\\d{6}).*", "$1"));
            return null;
        }).when(emailSender).send(eq(email), anyString(), anyString(), anyString());
        return userRepository.saveAndFlush(new User(email, "x", "T"));
    }

    @Test
    void codeExpiresAfterTenMinutes() {
        User user = newUser("exp@example.com");
        Instant start = Instant.parse("2026-10-26T10:00:00Z");
        service.setClock(Clock.fixed(start, ZoneOffset.UTC));
        service.issue(user, Purpose.VERIFY_EMAIL);
        service.setClock(Clock.fixed(start.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertThrows(InvalidCodeException.class, () -> service.consume(user.getId(), Purpose.VERIFY_EMAIL, lastCode.get()));
    }

    @Test
    void codeWorksJustBeforeExpiry() {
        User user = newUser("ok@example.com");
        Instant start = Instant.parse("2026-10-26T10:00:00Z");
        service.setClock(Clock.fixed(start, ZoneOffset.UTC));
        service.issue(user, Purpose.VERIFY_EMAIL);
        service.setClock(Clock.fixed(start.plus(Duration.ofMinutes(9)), ZoneOffset.UTC));
        assertDoesNotThrow(() -> service.consume(user.getId(), Purpose.VERIFY_EMAIL, lastCode.get()));
    }

    @Test
    void newCodeOnlyAfterSixtySeconds() {
        User user = newUser("cool@example.com");
        Instant start = Instant.parse("2026-10-26T10:00:00Z");
        service.setClock(Clock.fixed(start, ZoneOffset.UTC));
        assertTrue(service.issue(user, Purpose.RESET_PASSWORD));
        service.setClock(Clock.fixed(start.plusSeconds(59), ZoneOffset.UTC));
        assertFalse(service.issue(user, Purpose.RESET_PASSWORD));
        service.setClock(Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC));
        assertTrue(service.issue(user, Purpose.RESET_PASSWORD));
    }

    @Test
    void failedEmailDoesNotBreakSignUpFlow() {
        User user = userRepository.saveAndFlush(new User("down@example.com", "x", "T"));
        doThrow(new RuntimeException("Resend down")).when(emailSender).send(anyString(), anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> service.issue(user, Purpose.VERIFY_EMAIL));
    }
}
