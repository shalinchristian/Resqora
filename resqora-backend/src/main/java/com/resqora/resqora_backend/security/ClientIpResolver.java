package com.resqora.resqora_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * gets the visitor's real IP for the per-IP limits (login attempts, code emails).
 *
 * on render every request goes through cloudflare and then render's own proxy, so getRemoteAddr()
 * is just the proxy's private address, same for everyone. cloudflare puts the real IP in
 * CF-Connecting-IP (and True-Client-IP) and overwrites whatever the visitor sent, so those are
 * safe to trust. not using X-Forwarded-For because render keeps the visitor's own value at the
 * front of it, so it can be faked.
 *
 * the headers are only trusted if the request came from a private address (a proxy in front of
 * the app), so someone hitting the app directly from the internet can't pick their own IP.
 * set RESQORA_TRUST_PROXY_IP_HEADERS=false to always use getRemoteAddr().
 */
@Component
public class ClientIpResolver {
    // only digits, hex, dots and colons, so it's an IPv4/IPv6 address and never a hostname (no DNS lookup)
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
