package com.resqora.resqora_backend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/** Request metadata only: never retains a body, password, code, token or exception message. */
public final class LoginAudit {
    static final String ATTRIBUTE = LoginAudit.class.getName();
    static final String LOGGER_NAME = "resqora.audit.login";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public enum Reason {
        BAD_CREDENTIALS, USER_NOT_FOUND, EMAIL_NOT_VERIFIED, RATE_LIMITED,
        INVALID_CODE, EMAIL_ALREADY_VERIFIED, VALIDATION, AUTH_REJECTED, INTERNAL_ERROR
    }

    static final class Attempt {
        String email;
        Long userId;
        Reason reason;
        boolean authenticated;
    }

    private LoginAudit() { }

    public static void identify(String email, Long userId) {
        update(attempt -> {
            attempt.email = sanitize(email == null ? null : email.trim().toLowerCase(Locale.ROOT), 254);
            attempt.userId = userId;
        });
    }

    public static void failure(Reason reason) {
        update(attempt -> attempt.reason = reason);
    }

    public static void authenticated() {
        update(attempt -> attempt.authenticated = true);
    }

    // No state on pooled threads: the filter owns the attempt on the servlet request.
    // Also harmless when AuthService is used outside an HTTP request (tests, internal calls).
    private static void update(Consumer<Attempt> action) {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                    && attrs.getRequest().getAttribute(ATTRIBUTE) instanceof Attempt attempt) {
                action.accept(attempt);
            }
        } catch (RuntimeException ignored) {
            // Observability must never change authentication behavior.
        }
    }

    static void write(Attempt attempt, String ip, int status, boolean completed) {
        try {
            boolean success = completed && status >= 200 && status < 300 && attempt.authenticated;
            Reason reason = attempt.reason;
            if (!success && reason == null) {
                reason = !completed || status >= 500 ? Reason.INTERNAL_ERROR
                        : status == 429 ? Reason.RATE_LIMITED
                        : status == 401 || status == 403 ? Reason.AUTH_REJECTED : Reason.VALIDATION;
            }
            String line = "login outcome=" + (success ? "SUCCESS" : "FAILURE")
                    + " time=" + OffsetDateTime.now(IST).withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    + " userId=" + (attempt.userId == null ? "-" : attempt.userId)
                    + " email=" + sanitize(attempt.email, 254) + " ip=" + sanitize(ip, 64)
                    + (success ? "" : " reason=" + reason.name());
            if (success) {
                LOG.info(line);
            } else {
                LOG.warn(line);
            }
        } catch (RuntimeException ignored) {
            // A broken logging backend must never break login; do not log the exception either.
        }
    }

    // Keep key=value unambiguous: remove controls, line separators, whitespace and delimiter tricks.
    static String sanitize(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        StringBuilder safe = new StringBuilder();
        value.codePoints().limit(maxLength).forEach(c -> safe.appendCodePoint(
                Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)
                        || Character.getType(c) == Character.FORMAT || c == '=' || c == '"' || c == '\\'
                        ? '_' : c));
        return safe.toString();
    }
}
