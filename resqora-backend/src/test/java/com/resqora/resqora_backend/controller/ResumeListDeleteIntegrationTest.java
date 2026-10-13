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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeListDeleteIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JwtService jwtService;

    private User owner;
    private User other;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new User("owner-" + UUID.randomUUID() + "@example.com", "hash"));
        other = userRepository.save(new User("other-" + UUID.randomUUID() + "@example.com", "hash"));
        ownerToken = "Bearer " + jwtService.generateToken(owner);
    }

    private Resume saveResume(User user, String name) {
        return resumeRepository.save(new Resume(user, name, "PDF", "text"));
    }

    @Test
    void listReturnsOnlyOwnResumesNewestFirst() throws Exception {
        saveResume(owner, "first.pdf");
        saveResume(owner, "second.pdf");
        saveResume(other, "not-mine.pdf");

        mockMvc.perform(get("/api/resumes").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.resumes.length()").value(2))
                .andExpect(jsonPath("$.resumes[0].originalFileName").value("second.pdf"))
                .andExpect(jsonPath("$.resumes[1].originalFileName").value("first.pdf"))
                .andExpect(jsonPath("$.resumes[0].documentType").value("PDF"))
                .andExpect(jsonPath("$.resumes[0].uploadedAt").exists());
    }

    @Test
    void listIsEmptyForNewUser() throws Exception {
        mockMvc.perform(get("/api/resumes").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.resumes.length()").value(0));
    }

    @Test
    void deleteOwnResumeReturns204() throws Exception {
        Resume resume = saveResume(owner, "mine.pdf");

        mockMvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", ownerToken))
                .andExpect(status().isNoContent());

        assertFalse(resumeRepository.existsById(resume.getId()));
    }

    @Test
    void deleteAnotherUsersResumeReturns404AndKeepsIt() throws Exception {
        Resume resume = saveResume(other, "theirs.pdf");

        mockMvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found"));

        assertTrue(resumeRepository.existsById(resume.getId()));
    }

    @Test
    void deleteMissingResumeReturns404() throws Exception {
        mockMvc.perform(delete("/api/resumes/999999").header("Authorization", ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void sixthUploadReturns409() throws Exception {
        for (int i = 1; i <= 5; i++) {
            saveResume(owner, "resume-" + i + ".pdf");
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "sixth.pdf", "application/pdf", "anything".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload").file(file).header("Authorization", ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Resume limit reached (5)"));
    }

    @Test
    void listAndDeleteRequireToken() throws Exception {
        mockMvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/resumes/1")).andExpect(status().isUnauthorized());
    }
}
