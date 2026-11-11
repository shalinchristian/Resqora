package com.resqora.resqora_backend.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.resqora.resqora_backend.security.ClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginAuditTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoginAudit.LOGGER_NAME);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void attach() {
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
    }
    @AfterEach void detach() {
        logger.detachAppender(logs); logs.stop(); RequestContextHolder.resetRequestAttributes();
    }

    @Test void timestampUsesIstEvenWhenServerRunsInAnotherZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var attempt = new LoginAudit.Attempt();
            attempt.authenticated = true;
            LoginAudit.write(attempt, "::1", 200, true);
            String timestamp = logs.list.getFirst().getFormattedMessage().split(" time=")[1].split(" ")[0];
            assertTrue(timestamp.endsWith("+05:30"));
            assertTrue(Duration.between(OffsetDateTime.parse(timestamp).toInstant(), Instant.now()).abs().toSeconds() < 3);
        } finally { TimeZone.setDefault(original); }
    }

    @Test void sanitizesAllFieldSeparatorsAndCapsLength() {
        assertEquals("a__b_c_d_e_f_g_h_i", LoginAudit.sanitize("a\r\nb\tc=d\u0000e\u2028f\u2029g\u0085h\u202ei", 254));
        assertEquals(254, LoginAudit.sanitize("a".repeat(10000), 254).length());
        assertEquals("-", LoginAudit.sanitize(null, 254));
    }

    @Test void loggingBackendFailureDoesNotBreakFilterOrSuccessfulResponse() throws Exception {
        @SuppressWarnings("unchecked") Appender<ILoggingEvent> broken = mock(Appender.class);
        doThrow(new IllegalStateException("broken appender")).when(broken).doAppend(any());
        logger.addAppender(broken);
        try {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            var response = new MockHttpServletResponse();
            new LoginAuditFilter(new ClientIpResolver(true)).doFilter(request, response, (req, res) -> {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                LoginAudit.identify("test@example.com", 42L);
                LoginAudit.authenticated();
                res.getWriter().write("unchanged");
            });
            assertEquals(200, response.getStatus());
            assertEquals("unchanged", response.getContentAsString());
            assertNull(request.getAttribute(LoginAudit.ATTRIBUTE));
        } finally { logger.detachAppender(broken); }
    }

    @Test void unexpectedFailureIsLoggedOnceAndRethrownWithoutExceptionText() {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        IOException failure = new IOException("secret exception token code password");
        assertSame(failure, assertThrows(IOException.class, () ->
                new LoginAuditFilter(new ClientIpResolver(true)).doFilter(request, new MockHttpServletResponse(),
                        (req, res) -> { throw failure; })));
        assertEquals(1, logs.list.size());
        String line = logs.list.getFirst().getFormattedMessage();
        assertTrue(line.endsWith("reason=INTERNAL_ERROR"));
        assertFalse(line.contains("secret"));
        assertNull(logs.list.getFirst().getThrowableProxy());
    }

    @Test void fallbackReasonsCoverSecurityValidationAndServerErrors() {
        int[] statuses = {403, 401, 429, 400, 415, 500};
        String[] reasons = {"AUTH_REJECTED", "AUTH_REJECTED", "RATE_LIMITED", "VALIDATION", "VALIDATION", "INTERNAL_ERROR"};
        for (int i = 0; i < statuses.length; i++) {
            LoginAudit.write(new LoginAudit.Attempt(), "127.0.0.1", statuses[i], true);
            assertTrue(logs.list.get(i).getFormattedMessage().endsWith("reason=" + reasons[i]));
        }
    }

    @Test void proxyTrustCanBeDisabledForAuditAndStateDoesNotLeakToNextRequest() throws Exception {
        var filter = new LoginAuditFilter(new ClientIpResolver(false));
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.1.2.3"); request.addHeader("CF-Connecting-IP", "203.0.113.42");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            LoginAudit.identify("user@example.com", 3L);
            LoginAudit.authenticated();
        });
        assertTrue(logs.list.getFirst().getFormattedMessage().endsWith("email=user@example.com ip=10.1.2.3"));
        RequestContextHolder.resetRequestAttributes();
        var next = new MockHttpServletRequest("POST", "/api/auth/verify-email");
        next.setRemoteAddr("127.0.0.1");
        filter.doFilter(next, new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(400));
        assertTrue(logs.list.getLast().getFormattedMessage().contains("userId=- email=- ip=127.0.0.1 reason=VALIDATION"));
    }

    @Test void encodedLoginPathCannotBypassAudit() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/%6cogin");
        request.setRemoteAddr("127.0.0.1");
        new LoginAuditFilter(new ClientIpResolver(true)).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> ((MockHttpServletResponse) res).setStatus(400));
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.getFirst().getFormattedMessage().endsWith("reason=VALIDATION"));
    }

    @Test void metadataUpdatesOutsideRequestAreHarmless() {
        assertDoesNotThrow(() -> { LoginAudit.identify("test@example.com", 1L); LoginAudit.failure(LoginAudit.Reason.INVALID_CODE); LoginAudit.authenticated(); });
        assertTrue(logs.list.isEmpty());
    }
}
