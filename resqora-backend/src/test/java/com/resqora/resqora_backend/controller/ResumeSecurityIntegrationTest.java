package com.resqora.resqora_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsParsedResumeRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/resumes/1/parsed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsParsedResumeRequestWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/resumes/1/parsed")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnalysisRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/resumes/1/analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnalysisRequestWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/resumes/1/analysis")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}