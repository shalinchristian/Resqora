package com.resqora.resqora_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {
    private final ClientIpResolver resolver = new ClientIpResolver(true);

    private static MockHttpServletRequest from(String remote) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        return request;
    }

    @Test
    void behindRendersProxyUsesCloudflaresVisitorIp() {
        MockHttpServletRequest request = from("10.21.157.68");
        request.addHeader("CF-Connecting-IP", "99.61.165.29");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 99.61.165.29, 104.22.17.40");
        assertEquals("99.61.165.29", resolver.resolve(request));
    }

    @Test
    void twoVisitorsBehindTheSameProxyGetDifferentIps() {
        MockHttpServletRequest first = from("10.0.0.5");
        first.addHeader("CF-Connecting-IP", "203.0.113.7");
        MockHttpServletRequest second = from("10.0.0.5");
        second.addHeader("True-Client-IP", "2001:db8::42");
        assertEquals("203.0.113.7", resolver.resolve(first));
        assertEquals("2001:db8::42", resolver.resolve(second));
    }

    @Test
    void xForwardedForAloneIsIgnoredBecauseItCanBeFaked() {
        MockHttpServletRequest request = from("10.0.0.5");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        assertEquals("10.0.0.5", resolver.resolve(request));
    }

    @Test
    void directInternetCallerCannotChooseItsIp() {
        MockHttpServletRequest request = from("198.51.100.9");
        request.addHeader("CF-Connecting-IP", "1.1.1.1");
        assertEquals("198.51.100.9", resolver.resolve(request));
    }

    @Test
    void headerThatIsNotAnIpIsIgnored() {
        MockHttpServletRequest request = from("10.0.0.5");
        request.addHeader("CF-Connecting-IP", "evil.example.com");
        assertEquals("10.0.0.5", resolver.resolve(request));
    }

    @Test
    void canBeSwitchedOff() {
        MockHttpServletRequest request = from("10.0.0.5");
        request.addHeader("CF-Connecting-IP", "203.0.113.7");
        assertEquals("10.0.0.5", new ClientIpResolver(false).resolve(request));
    }

    @Test
    void privateRanges() {
        assertTrue(ClientIpResolver.isPrivate("10.1.2.3"));
        assertTrue(ClientIpResolver.isPrivate("172.20.0.1"));
        assertTrue(ClientIpResolver.isPrivate("192.168.1.1"));
        assertTrue(ClientIpResolver.isPrivate("100.64.0.1"));
        assertTrue(ClientIpResolver.isPrivate("127.0.0.1"));
        assertFalse(ClientIpResolver.isPrivate("172.32.0.1"));
        assertFalse(ClientIpResolver.isPrivate("8.8.8.8"));
    }
}
