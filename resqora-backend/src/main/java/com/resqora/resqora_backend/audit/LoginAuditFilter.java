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

/** sits around security + MVC so it also sees validation failures before the controller */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LoginAuditFilter extends OncePerRequestFilter {
    private final ClientIpResolver clientIpResolver;

    public LoginAuditFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // match the decoded path like MVC does, otherwise %6cogin would skip the audit
        String path;
        try {
            path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        } catch (IllegalArgumentException ignored) {
            // bad path encoding is already handled by servlet/security, leave it
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
                // IP lookup is in here too. if auditing fails the response should be unaffected
            } finally {
                request.removeAttribute(LoginAudit.ATTRIBUTE);
            }
        }
    }
}
