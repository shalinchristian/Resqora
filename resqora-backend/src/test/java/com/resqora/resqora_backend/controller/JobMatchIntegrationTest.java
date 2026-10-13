package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobMatchIntegrationTest {
    private static final String RESUME_TEXT = """
            Alex Johnson
            alex@example.com
            SKILLS
            Java, Spring Boot, PostgreSQL
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JwtService jwtService;

    private Resume resume;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("match-" + UUID.randomUUID() + "@example.com", "hash"));
        User other = userRepository.save(new User("match-other-" + UUID.randomUUID() + "@example.com", "hash"));
        resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", RESUME_TEXT));
        token = "Bearer " + jwtService.generateToken(user);
        otherToken = "Bearer " + jwtService.generateToken(other);
    }

    private String body(String jobDescription) {
        return "{\"jobDescription\":\"" + jobDescription + "\"}";
    }

    @Test
    void returnsMatchScoreAndSkills() throws Exception {
        mockMvc.perform(post("/api/resumes/" + resume.getId() + "/match")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Java backend developer: PostgreSQL and Docker required")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchScore").value(67))
                .andExpect(jsonPath("$.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.matchedSkills[1]").value("PostgreSQL"))
                .andExpect(jsonPath("$.missingSkills[0]").value("Docker"));
    }

    @Test
    void blankJobDescriptionReturns400() throws Exception {
        mockMvc.perform(post("/api/resumes/" + resume.getId() + "/match")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("jobDescription")));
    }

    @Test
    void tooLongJobDescriptionReturns400() throws Exception {
        mockMvc.perform(post("/api/resumes/" + resume.getId() + "/match")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(10001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherUsersResumeReturns404() throws Exception {
        mockMvc.perform(post("/api/resumes/" + resume.getId() + "/match")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Java")))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(post("/api/resumes/" + resume.getId() + "/match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Java")))
                .andExpect(status().isUnauthorized());
    }
}
