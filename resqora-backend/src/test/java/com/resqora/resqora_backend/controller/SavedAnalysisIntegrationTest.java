package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.ResumeAnalysis;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeAnalysisRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SavedAnalysisIntegrationTest {
    private static final String RESUME_TEXT = """
            Alex Johnson
            alex@example.com
            SKILLS
            Java, Spring Boot, PostgreSQL
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired private JwtService jwtService;

    private Resume resume;
    private String token;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("analysis-" + UUID.randomUUID() + "@example.com", "hash"));
        resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", RESUME_TEXT));
        token = "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void firstRequestSavesAnalysisAndSecondReturnsTheSameStoredResult() throws Exception {
        assertTrue(resumeAnalysisRepository.findByResumeId(resume.getId()).isEmpty());

        String first = mockMvc.perform(get("/api/resumes/" + resume.getId() + "/analysis")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andExpect(jsonPath("$.findings").isArray())
                .andReturn().getResponse().getContentAsString();

        ResumeAnalysis saved = resumeAnalysisRepository.findByResumeId(resume.getId()).orElseThrow();
        long rowsAfterFirst = resumeAnalysisRepository.count();

        String second = mockMvc.perform(get("/api/resumes/" + resume.getId() + "/analysis")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, second);
        assertEquals(rowsAfterFirst, resumeAnalysisRepository.count());
        assertEquals(saved.getId(), resumeAnalysisRepository.findByResumeId(resume.getId()).orElseThrow().getId());
    }

    @Test
    void deletingResumeAlsoDeletesItsAnalysis() throws Exception {
        mockMvc.perform(get("/api/resumes/" + resume.getId() + "/analysis").header("Authorization", token))
                .andExpect(status().isOk());
        assertTrue(resumeAnalysisRepository.findByResumeId(resume.getId()).isPresent());

        mockMvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());

        assertFalse(resumeAnalysisRepository.findByResumeId(resume.getId()).isPresent());
        assertFalse(resumeRepository.existsById(resume.getId()));
    }
}
