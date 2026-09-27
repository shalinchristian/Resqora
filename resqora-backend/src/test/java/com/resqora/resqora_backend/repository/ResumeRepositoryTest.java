package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ResumeRepositoryTest {
    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsResumeWithAssociatedUser() {
        User user = userRepository.saveAndFlush(new User("resume-owner@example.com", "hashed-password"));
        Resume resume = resumeRepository.saveAndFlush(
                new Resume(user, "resume.pdf", "PDF", "Resume text"));

        Resume savedResume = resumeRepository.findById(resume.getId()).orElseThrow();

        assertEquals("resume.pdf", savedResume.getOriginalFileName());
        assertEquals("PDF", savedResume.getDocumentType());
        assertEquals("Resume text", savedResume.getExtractedText());
        assertEquals(user.getId(), savedResume.getUser().getId());
    }
}