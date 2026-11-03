package com.resqora.resqora_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Finds the visitor's real IP address for the per-IP limits (login attempts, code emails).
 *
 * On Render every request arrives through Cloudflare and then Render's own proxy, so
 * getRemoteAddr() is the proxy's private address and is the same for every visitor. Cloudflare
 * puts the real visitor IP in CF-Connecting-IP (and True-Client-IP), replacing any value the
 * visitor sent, so those headers can be trusted. X-Forwarded-For is not used: Render keeps
 * whatever the visitor sent at the front of it, so it can be faked.
 *
 * The headers are only trusted when the request came from a private address (a proxy in front of
 * the app). Someone reaching the app directly from the internet can't pick their own IP this way.
 * Set RESQORA_TRUST_PROXY_IP_HEADERS=false to always use getRemoteAddr().
 */
@Component
public class ClientIpResolver {
    // digits, hex, dots and colons only: an IPv4 or IPv6 literal, never a hostname (no DNS lookups)
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final String[] HEADERS = {"CF-Connecting-IP", "True-Client-IP"};

    private final boolean trustProxyHeaders;

    public ClientIpResolver(@Value("${resqora.trust-proxy-ip-headers:true}") boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustProxyHeaders || !isPrivate(remote)) {
            return remote;
        }
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value != null) {
                value = value.trim();
                if (IP_LITERAL.matcher(value).matches()) {
                    return value;
                }
            }
        }
        return remote;
    }

    // 10/8, 172.16/12, 192.168/16, 100.64/10 (carrier-grade NAT), 127/8 loopback, IPv6 loopback and fc00::/7
    static boolean isPrivate(String address) {
        if (address == null) {
            return false;
        }
        String a = address.toLowerCase(java.util.Locale.ROOT);
        if (a.equals("::1") || a.equals("0:0:0:0:0:0:0:1") || a.startsWith("fc") || a.startsWith("fd")) {
            return true;
        }
        String[] parts = a.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 10
                    || first == 127
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 100 && second >= 64 && second <= 127);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
