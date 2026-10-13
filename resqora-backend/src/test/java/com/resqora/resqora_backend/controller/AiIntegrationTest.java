package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AiUnavailableException;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import com.resqora.resqora_backend.service.ai.AiClient;
import com.resqora.resqora_backend.service.ai.GeminiAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiIntegrationTest {
    private static final String RESUME_TEXT = """
            Alex Johnson
            alex@example.com
            SKILLS
            Java, Spring Boot, PostgreSQL
            """;

    // Fake AI: no network, returns canned JSON and counts calls
    static class FakeAiClient implements AiClient {
        boolean configured = true;
        boolean fail = false;
        String reply = "{}";
        int calls = 0;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String generateJson(String prompt) {
            calls++;
            if (fail) {
                throw new AiUnavailableException();
            }
            return reply;
        }
    }

    @TestConfiguration
    static class FakeAiConfig {
        @Bean
        @Primary
        FakeAiClient fakeAiClient() {
            return new FakeAiClient();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JwtService jwtService;
    @Autowired private FakeAiClient fakeAi;

    private Resume resume;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        fakeAi.configured = true;
        fakeAi.fail = false;
        fakeAi.calls = 0;
        User user = userRepository.save(new User("ai-" + UUID.randomUUID() + "@example.com", "hash"));
        User other = userRepository.save(new User("ai-other-" + UUID.randomUUID() + "@example.com", "hash"));
        resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", RESUME_TEXT));
        token = "Bearer " + jwtService.generateToken(user);
        otherToken = "Bearer " + jwtService.generateToken(other);
    }

    private ResultActions ai(String auth, String body) throws Exception {
        return mockMvc.perform(post("/api/resumes/" + resume.getId() + "/ai")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void interviewQuestionsReturnsFiveItems() throws Exception {
        fakeAi.reply = "{\"items\":[\"Q1\",\"Q2\",\"Q3\",\"Q4\",\"Q5\",\"Q6\"]}";

        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("INTERVIEW_QUESTIONS"))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0]").value("Q1"))
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.remainingToday").value(9));
    }

    @Test
    void shouldIApplyReturnsVerdictText() throws Exception {
        fakeAi.reply = "{\"text\":\"YES: strong Java and PostgreSQL match.\"}";

        ai(token, "{\"action\":\"SHOULD_I_APPLY\",\"jobDescription\":\"Java developer\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("SHOULD_I_APPLY"))
                .andExpect(jsonPath("$.text").value("YES: strong Java and PostgreSQL match."))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rewriteWeakPointsReturnsItems() throws Exception {
        fakeAi.reply = "{\"items\":[\"Built a REST API with Spring Boot\",\"Designed PostgreSQL schema\"]}";

        ai(token, "{\"action\":\"REWRITE_WEAK_POINTS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1]").value("Designed PostgreSQL schema"));
    }

    @Test
    void sameRequestIsServedFromCacheWithoutCallingAi() throws Exception {
        fakeAi.reply = "{\"items\":[\"Q1\",\"Q2\",\"Q3\",\"Q4\",\"Q5\"]}";
        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\"}").andExpect(status().isOk());
        assertEquals(1, fakeAi.calls);

        fakeAi.reply = "{\"items\":[\"different\"]}";
        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("Q1"))
                .andExpect(jsonPath("$.remainingToday").value(9));
        assertEquals(1, fakeAi.calls);
    }

    @Test
    void eleventhRealCallReturns429() throws Exception {
        fakeAi.reply = "{\"items\":[\"Q1\"]}";
        for (int i = 1; i <= 10; i++) {
            ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\",\"jobDescription\":\"job " + i + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remainingToday").value(10 - i));
        }
        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\",\"jobDescription\":\"job 11\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Daily AI limit reached"));
        assertEquals(10, fakeAi.calls);

        // cached answers are still available after the limit
        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\",\"jobDescription\":\"job 3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingToday").value(0));
    }

    @Test
    void shouldIApplyWithoutJobDescriptionReturns400() throws Exception {
        ai(token, "{\"action\":\"SHOULD_I_APPLY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("jobDescription is required for SHOULD_I_APPLY"));
        assertEquals(0, fakeAi.calls);
    }

    @Test
    void missingOrUnknownActionReturns400() throws Exception {
        ai(token, "{}").andExpect(status().isBadRequest());
        ai(token, "{\"action\":\"WRITE_MY_THESIS\"}").andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersResumeReturns404() throws Exception {
        ai(otherToken, "{\"action\":\"INTERVIEW_QUESTIONS\"}").andExpect(status().isNotFound());
        assertEquals(0, fakeAi.calls);
    }

    @Test
    void noApiKeyReturns503() throws Exception {
        fakeAi.configured = false;
        ai(token, "{\"action\":\"INTERVIEW_QUESTIONS\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service not available right now"));
        assertEquals(0, fakeAi.calls);
    }

    @Test
    void aiFailureReturns503AndIsNotCached() throws Exception {
        fakeAi.fail = true;
        ai(token, "{\"action\":\"REWRITE_WEAK_POINTS\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("AI service not available right now"));

        fakeAi.fail = false;
        fakeAi.reply = "not json at all";
        ai(token, "{\"action\":\"REWRITE_WEAK_POINTS\"}").andExpect(status().isServiceUnavailable());

        fakeAi.reply = "{\"items\":[\"ok\"]}";
        ai(token, "{\"action\":\"REWRITE_WEAK_POINTS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingToday").value(9));
    }

    @Test
    void geminiClientWithoutKeyIsNotConfigured() {
        assertFalse(new GeminiAiClient("", "gemini-3.5-flash-lite").isConfigured());
    }
}
