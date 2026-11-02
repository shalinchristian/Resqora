package com.resqora.resqora_backend.service.email;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The request Resend receives carries both the HTML and the plain-text version.
class ResendEmailSenderTest {
    @Test
    void sendsHtmlAndTextToResend() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"id\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            new ResendEmailSender("test-key", "Resqora <noreply@example.com>", baseUrl, false)
                    .send("user@example.com", "Subject", "plain text", "<p>html</p>");
        } finally {
            server.stop(0);
        }
        JsonNode json = JsonMapper.builder().build().readTree(body.get());
        assertEquals("Bearer test-key", auth.get());
        assertEquals("user@example.com", json.get("to").get(0).asString());
        assertEquals("Subject", json.get("subject").asString());
        assertEquals("plain text", json.get("text").asString());
        assertEquals("<p>html</p>", json.get("html").asString());
    }
}
