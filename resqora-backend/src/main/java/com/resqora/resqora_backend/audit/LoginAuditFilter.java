package com.resqora.resqora_backend.audit;

import com.resqora.resqora_backend.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/** Wraps security and MVC, including validation failures before a controller is called. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LoginAuditFilter extends OncePerRequestFilter {
    private final ClientIpResolver clientIpResolver;

    public LoginAuditFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Match decoded application paths, just like MVC: %6cogin must not bypass auditing.
        String path;
        try {
            path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        } catch (IllegalArgumentException ignored) {
            // Leave invalid path encoding to the existing servlet/security handling.
            return true;
        }
        return !"POST".equals(request.getMethod())
                || !(path.equals("/api/auth/login") || path.equals("/api/auth/verify-email"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LoginAudit.Attempt attempt = new LoginAudit.Attempt();
        request.setAttribute(LoginAudit.ATTRIBUTE, attempt);
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                LoginAudit.write(attempt, clientIpResolver.resolve(request), response.getStatus(), completed);
            } catch (RuntimeException ignored) {
                // Includes IP resolution: audit failure must never affect the HTTP response.
            } finally {
                request.removeAttribute(LoginAudit.ATTRIBUTE);
            }
        }
    }
}
