package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardDataIntegrationTest {
    private static final String RESUME_TEXT = "Alex Johnson\nalex@example.com\nSKILLS\nJava, Spring Boot\n";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private AiUsageRepository usageRepository;
    @Autowired private JwtService jwtService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("dash-" + UUID.randomUUID() + "@example.com", "hash", "Dash"));
        token = "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void listShowsNullScoreBeforeAnalysisAndScoreAfter() throws Exception {
        Resume analysed = resumeRepository.save(new Resume(user, "analysed.pdf", "PDF", RESUME_TEXT));
        resumeRepository.save(new Resume(user, "fresh.pdf", "PDF", RESUME_TEXT));

        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumes[0].overallScore").isEmpty())
                .andExpect(jsonPath("$.resumes[1].overallScore").isEmpty());

        String body = mockMvc.perform(get("/api/resumes/" + analysed.getId() + "/analysis").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int score = com.jayway.jsonpath.JsonPath.read(body, "$.overallScore");

        // newest first: fresh.pdf is [0] (no analysis), analysed.pdf is [1]
        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumes[0].originalFileName").value("fresh.pdf"))
                .andExpect(jsonPath("$.resumes[0].overallScore").isEmpty())
                .andExpect(jsonPath("$.resumes[1].originalFileName").value("analysed.pdf"))
                .andExpect(jsonPath("$.resumes[1].overallScore").value(score));
    }

    @Test
    void usageIsZeroForNewUser() throws Exception {
        mockMvc.perform(get("/api/ai/usage").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedToday").value(0))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.remainingToday").value(10));
    }

    @Test
    void usageCountsOnlyOwnCallsAndNeverGoesNegative() throws Exception {
        User other = userRepository.save(new User("dash-other-" + UUID.randomUUID() + "@example.com", "hash"));
        for (int i = 0; i < 3; i++) {
            usageRepository.save(new AiUsage(user));
        }
        usageRepository.save(new AiUsage(other));

        mockMvc.perform(get("/api/ai/usage").header("Authorization", token))
                .andExpect(jsonPath("$.usedToday").value(3))
                .andExpect(jsonPath("$.remainingToday").value(7));

        for (int i = 0; i < 9; i++) {
            usageRepository.save(new AiUsage(user));
        }
        mockMvc.perform(get("/api/ai/usage").header("Authorization", token))
                .andExpect(jsonPath("$.usedToday").value(12))
                .andExpect(jsonPath("$.remainingToday").value(0));
    }

    @Test
    void usageRequiresToken() throws Exception {
        mockMvc.perform(get("/api/ai/usage")).andExpect(status().isUnauthorized());
    }
}
