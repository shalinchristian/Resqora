package com.resqora.resqora_backend.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.EmailCodeService;
import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import com.resqora.resqora_backend.service.email.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "resqora.email.require-verification=true",
        "resqora.login.max-attempts-per-email=2",
        "resqora.login.max-attempts-per-ip=1000",
        "resqora.trust-proxy-ip-headers=true"})
@AutoConfigureMockMvc
class LoginAuditIntegrationTest {
    private static final String PASSWORD = "Audit-password-DO-NOT-LOG!";
    private static final String WRONG = "Wrong-password-DO-NOT-LOG!";
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoginAudit.LOGGER_NAME);
    private ListAppender<ILoggingEvent> logs;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired EmailCodeService codes;
    @Autowired FakeEmails emails;

    static class FakeEmails implements EmailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        public void send(String to, String subject, String text, String html) {
            var matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
            assertTrue(matcher.find());
            codes.put(to, matcher.group(1));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeEmails auditEmails() { return new FakeEmails(); }
    }

    @BeforeEach void attach() {
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach void detach() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private User user(boolean verified) {
        User user = new User("audit-" + UUID.randomUUID() + "@example.com", passwords.encode(PASSWORD), "Audit Test");
        user.setEmailVerified(verified);
        return users.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)));
    }

    private MockHttpServletRequestBuilder verify(String email, String code) {
        return post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "code", code)));
    }

    private String one(boolean success, LoginAudit.Reason reason, Long id, String email, String ip) {
        assertEquals(1, logs.list.size(), "exactly one audit event per attempt");
        var event = logs.list.getFirst();
        assertEquals(success ? Level.INFO : Level.WARN, event.getLevel());
        assertEquals(LoginAudit.LOGGER_NAME, event.getLoggerName());
        assertNull(event.getThrowableProxy());
        String line = event.getFormattedMessage();
        assertTrue(line.startsWith("login outcome=" + (success ? "SUCCESS" : "FAILURE") + " time="), line);
        assertTrue(line.contains(" userId=" + (id == null ? "-" : id) + " email=" + email + " ip=" + ip), line);
        if (success) assertFalse(line.contains("reason="));
        else assertTrue(line.endsWith(" reason=" + reason), line);
        String time = line.split(" time=")[1].split(" ")[0];
        assertTrue(time.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+05:30"), time);
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), OffsetDateTime.parse(time).getOffset());
        assertFalse(line.contains(PASSWORD));
        assertFalse(line.contains(WRONG));
        assertFalse(line.contains("Bearer "));
        assertFalse(line.contains("eyJ"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
        return line;
    }

    @Test void successNormalizesEmailAndNeverLogsReturnedToken() throws Exception {
        User user = user(true);
        MvcResult result = mvc.perform(login("  " + user.getEmail().toUpperCase(java.util.Locale.ROOT) + "  ", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        String line = one(true, null, user.getId(), user.getEmail(), "127.0.0.1");
        String token = json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        assertFalse(token.isBlank());
        assertFalse(line.contains(token));
    }

    @Test void wrongPasswordAndUnknownEmailKeepIdenticalHttpResponse() throws Exception {
        User user = user(true);
        var wrong = mvc.perform(login(user.getEmail(), WRONG)).andExpect(status().isUnauthorized()).andReturn();
        one(false, LoginAudit.Reason.BAD_CREDENTIALS, user.getId(), user.getEmail(), "127.0.0.1");
        logs.list.clear();
        String missing = "absent-" + UUID.randomUUID() + "@example.com";
        var unknown = mvc.perform(login(missing, WRONG)).andExpect(status().isUnauthorized()).andReturn();
        one(false, LoginAudit.Reason.USER_NOT_FOUND, null, missing, "127.0.0.1");
        assertEquals(wrong.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
    }

    @Test void unverifiedEmail() throws Exception {
        User user = user(false);
        mvc.perform(login(user.getEmail(), PASSWORD)).andExpect(status().isForbidden());
        one(false, LoginAudit.Reason.EMAIL_NOT_VERIFIED, user.getId(), user.getEmail(), "127.0.0.1");
    }

    @Test void rateLimitedAttemptStillHasExactlyOneEvent() throws Exception {
        User user = user(true);
        for (int i = 0; i < 2; i++) {
            logs.list.clear();
            mvc.perform(login(user.getEmail(), WRONG)).andExpect(status().isUnauthorized());
            one(false, LoginAudit.Reason.BAD_CREDENTIALS, user.getId(), user.getEmail(), "127.0.0.1");
        }
        logs.list.clear();
        mvc.perform(login(user.getEmail(), PASSWORD)).andExpect(status().isTooManyRequests());
        one(false, LoginAudit.Reason.RATE_LIMITED, null, user.getEmail(), "127.0.0.1");
    }

    @Test void deletedAccountIsIndistinguishableFromMissingAccount() throws Exception {
        User user = user(true);
        user.markDeleted(Instant.now());
        users.saveAndFlush(user);
        mvc.perform(login(user.getEmail(), PASSWORD)).andExpect(status().isUnauthorized());
        // The existing active-only repository query never returns the deleted row.
        one(false, LoginAudit.Reason.USER_NOT_FOUND, null, user.getEmail(), "127.0.0.1");
    }

    @Test void verificationSuccessAndAlreadyVerifiedNeverLogCodeOrToken() throws Exception {
        User user = user(false);
        codes.issue(user, Purpose.VERIFY_EMAIL);
        String code = emails.codes.get(user.getEmail());
        var result = mvc.perform(verify(user.getEmail(), code)).andExpect(status().isOk()).andReturn();
        String line = one(true, null, user.getId(), user.getEmail(), "127.0.0.1");
        assertFalse(line.contains(code));
        assertFalse(line.contains(json.readTree(result.getResponse().getContentAsString()).get("token").asText()));
        logs.list.clear();
        mvc.perform(verify(user.getEmail(), code)).andExpect(status().isBadRequest());
        assertFalse(one(false, LoginAudit.Reason.EMAIL_ALREADY_VERIFIED, user.getId(), user.getEmail(), "127.0.0.1").contains(code));
    }

    @Test void invalidAndUnknownVerificationKeepIdenticalResponses() throws Exception {
        User user = user(false);
        codes.issue(user, Purpose.VERIFY_EMAIL);
        String code = "000000".equals(emails.codes.get(user.getEmail())) ? "111111" : "000000";
        var invalid = mvc.perform(verify(user.getEmail(), code)).andExpect(status().isBadRequest()).andReturn();
        assertFalse(one(false, LoginAudit.Reason.INVALID_CODE, user.getId(), user.getEmail(), "127.0.0.1").contains(code));
        logs.list.clear();
        String missing = "missing-verify@example.com";
        var unknown = mvc.perform(verify(missing, code)).andExpect(status().isBadRequest()).andReturn();
        one(false, LoginAudit.Reason.USER_NOT_FOUND, null, missing, "127.0.0.1");
        assertEquals(invalid.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
    }

    @Test void validationCapturesOnlyEmailAndSanitizesInjection() throws Exception {
        String email = "BAD@example.com\r\nforged outcome=SUCCESS\u0000\u001b\u2028";
        mvc.perform(login(email, PASSWORD)).andExpect(status().isBadRequest());
        String line = one(false, LoginAudit.Reason.VALIDATION, null,
                LoginAudit.sanitize(email.trim().toLowerCase(java.util.Locale.ROOT), 254), "127.0.0.1");
        assertEquals(1, line.lines().count());
        assertFalse(line.contains("outcome=SUCCESS"));
        assertTrue(line.codePoints().noneMatch(Character::isISOControl));
    }

    @Test void overlongEmailIsCappedInValidationEvent() throws Exception {
        String email = "a".repeat(600) + "@example.com";
        mvc.perform(login(email, PASSWORD)).andExpect(status().isBadRequest());
        one(false, LoginAudit.Reason.VALIDATION, null, "a".repeat(254), "127.0.0.1");
    }

    @Test void malformedJsonAndInvalidCodeFormatAreAuditedWithoutBody() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{broken:" + PASSWORD)).andExpect(status().isBadRequest());
        one(false, LoginAudit.Reason.VALIDATION, null, "-", "127.0.0.1");
        logs.list.clear();
        mvc.perform(verify("validation@example.com", "secret-invalid-code" )).andExpect(status().isBadRequest());
        assertFalse(one(false, LoginAudit.Reason.VALIDATION, null, "validation@example.com", "127.0.0.1")
                .contains("secret-invalid-code"));
    }

    @Test void cloudflareHeaderFromPrivateProxyIsUsed() throws Exception {
        User user = user(true);
        mvc.perform(login(user.getEmail(), PASSWORD).with(r -> { r.setRemoteAddr("10.1.2.3"); return r; })
                .header("CF-Connecting-IP", "203.0.113.42").header("X-Forwarded-For", "1.1.1.1"))
                .andExpect(status().isOk());
        one(true, null, user.getId(), user.getEmail(), "203.0.113.42");
    }

    @Test void directClientCannotSpoofProxyHeaders() throws Exception {
        User user = user(true);
        mvc.perform(login(user.getEmail(), PASSWORD).with(r -> { r.setRemoteAddr("198.51.100.24"); return r; })
                .header("CF-Connecting-IP", "203.0.113.99").header("True-Client-IP", "203.0.113.98"))
                .andExpect(status().isOk());
        one(true, null, user.getId(), user.getEmail(), "198.51.100.24");
    }

    @Test void trueClientIpFromPrivateProxyIsUsed() throws Exception {
        User user = user(true);
        mvc.perform(login(user.getEmail(), PASSWORD).with(r -> { r.setRemoteAddr("172.16.0.5"); return r; })
                .header("True-Client-IP", "2001:db8::42"))
                .andExpect(status().isOk());
        one(true, null, user.getId(), user.getEmail(), "2001:db8::42");
    }

    @Test void rejectionBeforeControllerIsAuditedOnceWithoutReadingBody() throws Exception {
        mvc.perform(login("pre-security@example.com", PASSWORD).header("Origin", "https://not-allowed.example"))
                .andExpect(status().isForbidden());
        one(false, LoginAudit.Reason.AUTH_REJECTED, null, "-", "127.0.0.1");
    }

    @Test void nonLoginEndpointDoesNotProduceLoginAudit() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"non-login@example.com\"}")).andExpect(status().isAccepted());
        assertTrue(logs.list.isEmpty());
    }
}
