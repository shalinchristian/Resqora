package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardChatFallbackHttpIntegrationTest {
    static HttpServer server;
    static final ExecutorService executor = Executors.newCachedThreadPool();
    static volatile CountDownLatch timeoutRelease = new CountDownLatch(1);
    static volatile String groqMode = "ok";
    static volatile boolean geminiFails;
    static final AtomicInteger groqCalls = new AtomicInteger(), geminiCalls = new AtomicInteger();
    static final String GROQ_OK = "{\"choices\":[{\"message\":{\"content\":\"groq HTTP reply\"}}]}";
    static final String GEMINI_OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"gemini HTTP reply\"}]}}]}";
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/groq/chat/completions", exchange -> {
                groqCalls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                if (groqMode.equals("network")) { exchange.close(); return; }
                if (groqMode.equals("timeout")) {
                    exchange.sendResponseHeaders(200, 100);
                    try { timeoutRelease.await(35, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { exchange.close(); }
                    return;
                }
                int status = groqMode.matches("\\d+") ? Integer.parseInt(groqMode) : 200;
                String body = groqMode.equals("malformed") ? "not-json" : groqMode.equals("empty")
                        ? "{\"choices\":[]}" : groqMode.equals("blank")
                        ? "{\"choices\":[{\"message\":{\"content\":\" \"}}]}" : GROQ_OK;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.createContext("/gemini/models/", exchange -> {
                geminiCalls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                byte[] bytes = GEMINI_OK.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(geminiFails ? 503 : 200, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.setExecutor(executor);
            server.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void providers(DynamicPropertyRegistry properties) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.add("resqora.ai.groq-base-url", () -> base + "/groq");
        properties.add("resqora.ai.gemini-base-url", () -> base + "/gemini");
        properties.add("resqora.ai.groq-api-key", () -> "local-test-key");
        properties.add("resqora.ai.gemini-api-key", () -> "local-test-key");
    }
    @AfterAll static void stopServer() { server.stop(0); executor.shutdownNow(); }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AiUsageRepository usage;
    @Autowired JwtService jwt;
    User user; String bearer;
    @BeforeEach void reset() {
        timeoutRelease = new CountDownLatch(1);
        groqMode = "ok"; geminiFails = false; groqCalls.set(0); geminiCalls.set(0);
        user = users.save(new User("fallback-" + UUID.randomUUID() + "@example.com", "hash"));
        bearer = "Bearer " + jwt.generateToken(user);
    }
    private org.springframework.test.web.servlet.ResultActions chat() throws Exception {
        return mvc.perform(post("/api/chat").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Help with my resume\"}"));
    }
    private void oneDashboardUse(AiUsage.Provider provider) throws Exception {
        assertEquals(1, usage.countByUserIdAndKindAndCreatedAtGreaterThanEqual(user.getId(), AiUsage.Kind.DASHBOARD_CHAT, Instant.EPOCH));
        var rows = usage.findAll().stream().filter(u -> u.getUser().getId().equals(user.getId())).toList();
        assertEquals(1, rows.size()); assertEquals(provider, rows.getFirst().getProvider());
        mvc.perform(get("/api/ai/usage").header("Authorization", bearer))
                .andExpect(jsonPath("$.dashboardChat.usedToday").value(1))
                .andExpect(jsonPath("$.dashboardChat.remainingToday").value(19))
                .andExpect(jsonPath("$.resumeChat.usedToday").value(0))
                .andExpect(jsonPath("$.resumeChat.remainingToday").value(20));
    }
    @Test void groqSuccessDoesNotCallGemini() throws Exception {
        chat().andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("groq HTTP reply"));
        assertEquals(1, groqCalls.get()); assertEquals(0, geminiCalls.get());
        oneDashboardUse(AiUsage.Provider.GROQ);
    }
    @ParameterizedTest @ValueSource(strings = {"500", "503", "429", "network", "malformed", "empty", "blank"})
    void anyGroqProviderFailureFallsBackAndCountsOnlyOnce(String failure) throws Exception {
        groqMode = failure;
        chat().andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("gemini HTTP reply"))
                .andExpect(jsonPath("$.remainingToday").value(19)).andExpect(jsonPath("$.limit").value(20));
        assertTrue(groqCalls.get() >= 1); assertEquals(1, geminiCalls.get());
        oneDashboardUse(AiUsage.Provider.GEMINI);
    }
    @Test void readTimeoutAlsoFallsBackAndCountsOnlyOnce() throws Exception {
        groqMode = "timeout";
        try {
            chat().andExpect(status().isOk()).andExpect(jsonPath("$.reply").value("gemini HTTP reply"));
            oneDashboardUse(AiUsage.Provider.GEMINI);
            assertEquals(1, geminiCalls.get());
        } finally { timeoutRelease.countDown(); }
    }
    @Test void fallbackHonorsGeminiAppCapAndRefundsOnTotalFailure() throws Exception {
        var rows = new java.util.ArrayList<AiUsage>();
        for (int i = 0; i < 450; i++) rows.add(new AiUsage(user, AiUsage.Kind.SUMMARY, AiUsage.Provider.GEMINI));
        usage.saveAllAndFlush(rows);
        groqMode = "503";
        try {
            chat().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
            assertEquals(0, geminiCalls.get());
            assertEquals(0, usage.countByUserIdAndKindAndCreatedAtGreaterThanEqual(user.getId(), AiUsage.Kind.DASHBOARD_CHAT, Instant.EPOCH));
        } finally { usage.deleteAll(rows); }
    }
    @Test void bothProvidersFailWithExisting503CodeAndRefund() throws Exception {
        groqMode = "503"; geminiFails = true;
        chat().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        assertEquals(1, groqCalls.get()); assertEquals(1, geminiCalls.get());
        assertEquals(0, usage.countByUserIdAndKindAndCreatedAtGreaterThanEqual(user.getId(), AiUsage.Kind.DASHBOARD_CHAT, Instant.EPOCH));
        mvc.perform(get("/api/ai/usage").header("Authorization", bearer))
                .andExpect(jsonPath("$.dashboardChat.remainingToday").value(20));
    }
    @Test void fallbackAtTwentiethMessageDoesNotReserveATwentyFirst() throws Exception {
        for (int i = 0; i < 19; i++) usage.save(new AiUsage(user, AiUsage.Kind.DASHBOARD_CHAT, AiUsage.Provider.GROQ));
        groqMode = "429";
        chat().andExpect(status().isOk()).andExpect(jsonPath("$.remainingToday").value(0));
        chat().andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("DASHBOARD_CHAT_LIMIT"));
        assertEquals(1, groqCalls.get()); assertEquals(1, geminiCalls.get());
        assertEquals(20, usage.countByUserIdAndKindAndCreatedAtGreaterThanEqual(user.getId(), AiUsage.Kind.DASHBOARD_CHAT, Instant.EPOCH));
    }
}
