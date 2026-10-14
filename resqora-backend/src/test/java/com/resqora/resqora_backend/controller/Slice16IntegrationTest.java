package com.resqora.resqora_backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import com.resqora.resqora_backend.service.ai.GeminiAiClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Slice 16: QA fixes (quota reservation, summary charging, history nulls, control chars, Retry-After,
// exact-2MB upload, configurable Gemini URL, delete-account password limiting)
@SpringBootTest
@AutoConfigureMockMvc
@Import(AiIntegrationTest.FakeAiConfig.class)
class Slice16IntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private AiUsageRepository usageRepository;
    @Autowired private JwtService jwtService;
    @Autowired private AiIntegrationTest.FakeAiClient fakeAi;

    private User user;
    private Resume resume;
    private String token;

    @BeforeEach
    void setUp() {
        fakeAi.configured = true;
        fakeAi.fail = false;
        fakeAi.reply = "ok";
        fakeAi.delayMs = 0;
        user = userRepository.save(new User("s16-" + UUID.randomUUID() + "@example.com", "hash"));
        resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", "Asha Rao\nSKILLS\nJava, Spring Boot"));
        token = "Bearer " + jwtService.generateToken(user);
    }

    @AfterEach
    void tearDown() {
        fakeAi.delayMs = 0;
    }

    private ResultActions chat(String body) throws Exception {
        return mockMvc.perform(post("/api/resumes/" + resume.getId() + "/chat").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions summary() throws Exception {
        return mockMvc.perform(get("/api/resumes/" + resume.getId() + "/summary").header("Authorization", token));
    }

    private long usedToday() {
        return usageRepository.findAll().stream().filter(u -> u.getUser().getId().equals(user.getId())).count();
    }

    private void fillUsage(int rows) {
        List<AiUsage> usage = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            usage.add(new AiUsage(user));
        }
        usageRepository.saveAll(usage);
    }

    // ---- 1. summary charging ----

    @Test
    void newSummaryUsesOneCallSavedSummaryIsFree() throws Exception {
        summary().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
        assertEquals(1, usedToday());
        summary().andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        assertEquals(1, usedToday());
    }

    @Test
    void newSummaryAtUserLimitIs429ButSavedSummaryStillWorks() throws Exception {
        fillUsage(10);
        int before = fakeAi.calls;
        summary().andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Daily AI limit reached"));
        assertEquals(before, fakeAi.calls);

        resume.setAiSummary("Saved earlier.");
        resumeRepository.save(resume);
        summary().andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("Saved earlier."));
    }

    // ---- 2. reservation ----

    @Test
    void failedAiCallRefundsTheReservation() throws Exception {
        fakeAi.fail = true;
        chat("{\"message\":\"hi\"}").andExpect(status().isServiceUnavailable());
        summary().andExpect(status().isServiceUnavailable());
        assertEquals(0, usedToday());
        fakeAi.fail = false;
        fakeAi.reply = "   ";
        chat("{\"message\":\"hi\"}").andExpect(status().isServiceUnavailable());
        assertEquals(0, usedToday());
        assertNull(resumeRepository.findById(resume.getId()).orElseThrow().getAiSummary());
    }

    @Test
    void concurrentChatsNeverGoPastTheDailyLimit() throws Exception {
        fillUsage(7); // 3 calls left
        fakeAi.delayMs = 300; // slow AI: every request is in flight at the same time
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                jobs.add(() -> chat("{\"message\":\"parallel\"}").andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                statuses.add(f.get());
            }
            assertEquals(3, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
            assertEquals(5, statuses.stream().filter(s -> s == 429).count(), statuses.toString());
            assertEquals(10, usedToday());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- 3 + 4. chat validation ----

    @Test
    void nullHistoryEntriesAndFieldsAre400() throws Exception {
        int before = fakeAi.calls;
        chat("{\"message\":\"hi\",\"history\":[null]}").andExpect(status().isBadRequest());
        chat("{\"message\":\"hi\",\"history\":[{\"role\":null,\"text\":\"t\"}]}").andExpect(status().isBadRequest());
        chat("{\"message\":\"hi\",\"history\":[{\"role\":\"user\",\"text\":null}]}").andExpect(status().isBadRequest());
        chat("{\"message\":\"hi\",\"history\":[{\"role\":\"user\"}]}").andExpect(status().isBadRequest());
        assertEquals(before, fakeAi.calls);
    }

    @Test
    void controlCharactersInChatAre400ButNewlinesAreFine() throws Exception {
        int before = fakeAi.calls;
        chat("{\"message\":\"a\\u0000b\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("control characters")));
        chat("{\"message\":\"a\\u0007b\"}").andExpect(status().isBadRequest());
        chat("{\"message\":\"hi\",\"history\":[{\"role\":\"user\",\"text\":\"x\\u0000y\"}]}")
                .andExpect(status().isBadRequest());
        assertEquals(before, fakeAi.calls);
        chat("{\"message\":\"line one\\nline two\\tok\",\"history\":[{\"role\":\"model\",\"text\":\"a\\r\\nb\"}]}")
                .andExpect(status().isOk());
    }

    @Test
    void retryAfterIsSecondsUntilNextIstMidnight() throws Exception {
        fillUsage(10);
        String retry = chat("{\"message\":\"hi\"}").andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");
        java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
        java.time.Instant now = java.time.Instant.now();
        long expected = java.time.Duration.between(now,
                java.time.LocalDate.now(ist).plusDays(1).atStartOfDay(ist).toInstant()).toSeconds();
        long actual = Long.parseLong(retry);
        assertTrue(Math.abs(actual - expected) <= 5, actual + " vs " + expected);
        assertTrue(actual >= 1 && actual <= 86400);
    }

    // ---- 4. exactly-2MB upload ----

    private static byte[] pdfOfExactSize(int size) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Asha Rao asha@example.com SKILLS Java Spring Boot PostgreSQL Docker");
                content.endText();
            }
            document.save(out);
            byte[] pdf = out.toByteArray();
            byte[] padded = Arrays.copyOf(pdf, size);
            Arrays.fill(padded, pdf.length, size, (byte) '\n'); // whitespace after %%EOF
            return padded;
        }
    }

    @Test
    void fileOfExactlyTwoMegabytesIsAcceptedOneByteMoreIs413() throws Exception {
        int twoMb = 2 * 1024 * 1024;
        mockMvc.perform(multipart("/api/resumes/upload").header("Authorization", token)
                        .file(new MockMultipartFile("file", "exact.pdf", "application/pdf", pdfOfExactSize(twoMb))))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/resumes/upload").header("Authorization", token)
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", pdfOfExactSize(twoMb + 1))))
                .andExpect(status().is(413));
    }

    @Test
    void mainConfigLeavesRoomForTheMultipartEnvelope() throws Exception {
        Properties props = new Properties();
        try (InputStream in = new java.io.FileInputStream("src/main/resources/application.properties")) {
            props.load(in);
        }
        assertEquals("2MB", props.getProperty("spring.servlet.multipart.max-file-size"));
        assertEquals("3MB", props.getProperty("spring.servlet.multipart.max-request-size"));
        assertEquals("${RESQORA_GEMINI_BASE_URL:" + GeminiAiClient.DEFAULT_BASE_URL + "}",
                props.getProperty("resqora.ai.gemini-base-url"));
    }

    @Test
    void geminiBaseUrlIsConfigurable() {
        GeminiAiClient custom = new GeminiAiClient("k", "m", "http://localhost:9999/stub");
        Object restClient = ReflectionTestUtils.getField(custom, "restClient");
        assertTrue(restClient != null);
        // the configured URL is used: a call to an unreachable stub fails as "AI unavailable", not elsewhere
        try {
            custom.generateText("rules", "prompt");
        } catch (com.resqora.resqora_backend.exception.AiUnavailableException expected) {
            assertTrue(String.valueOf(expected.getCause()).contains("localhost:9999/stub"),
                    String.valueOf(expected.getCause()));
            return;
        }
        throw new AssertionError("expected the stub URL to be called");
    }

    private String registerAndLogin(String password) throws Exception {
        String email = "s17-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private ResultActions changePassword(String auth, String current) throws Exception {
        return mockMvc.perform(put("/api/auth/password").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"newpass123\"}"));
    }

    private ResultActions deleteMe(String auth, String password) throws Exception {
        return mockMvc.perform(delete("/api/auth/me").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + password + "\"}"));
    }

    // ---- slice 17: change-password limiting, shared counter ----

    @Test
    void sixthWrongCurrentPasswordIs429AndBlocksDeleteToo() throws Exception {
        String auth = registerAndLogin("abc12345");
        for (int i = 0; i < 5; i++) {
            changePassword(auth, "wrong-pass1").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        }
        changePassword(auth, "abc12345").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many wrong passwords. Try again in 15 minutes."));
        deleteMe(auth, "abc12345").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void wrongPasswordsOnBothEndpointsShareOneCounter() throws Exception {
        String auth = registerAndLogin("abc12345");
        for (int i = 0; i < 3; i++) {
            changePassword(auth, "wrong-pass1").andExpect(status().isForbidden());
        }
        for (int i = 0; i < 2; i++) {
            deleteMe(auth, "wrong-pass1").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Password is incorrect"));
        }
        changePassword(auth, "abc12345").andExpect(status().isTooManyRequests());
    }

    @Test
    void correctPasswordResetsTheCounter() throws Exception {
        String auth = registerAndLogin("abc12345");
        for (int i = 0; i < 4; i++) {
            changePassword(auth, "wrong-pass1").andExpect(status().isForbidden());
        }
        changePassword(auth, "abc12345").andExpect(status().isNoContent());
        for (int i = 0; i < 4; i++) {
            changePassword(auth, "wrong-pass1").andExpect(status().isForbidden());
        }
        mockMvc.perform(put("/api/auth/password").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"newpass123\",\"newPassword\":\"third1234\"}"))
                .andExpect(status().isNoContent());
    }

    // ---- 5. delete-account password limiting ----

    @Test
    void sixthWrongPasswordOnDeleteIs429WithRetryAfter() throws Exception {
        String email = "s16del-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"abc12345\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"abc12345\"}")).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(body, "$.token");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(delete("/api/auth/me").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-pass\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Password is incorrect"));
        }
        // blocked now, even with the right password
        mockMvc.perform(delete("/api/auth/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"abc12345\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many wrong passwords. Try again in 15 minutes."));
        assertTrue(userRepository.findByEmail(email).isPresent());
    }
}
