package com.resqora.resqora_backend.security;

import com.resqora.resqora_backend.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtService(SECRET, 3_600_000));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesRequestWithValidBearerToken() throws Exception {
        MockHttpServletRequest request = requestWithToken(tokenWithExpiration(42L, Instant.now().plusSeconds(60)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(42L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void ignoresExpiredToken() throws Exception {
        MockHttpServletRequest request = requestWithToken(tokenWithExpiration(42L, Instant.now().minusSeconds(60)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void leavesRequestUnauthenticatedWithoutAuthorizationHeader() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String tokenWithExpiration(Long userId, Instant expiration) {
        return Jwts.builder()
                .subject(userId.toString())
                .expiration(Date.from(expiration))
                .signWith(SIGNING_KEY)
                .compact();
    }
}