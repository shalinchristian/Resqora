package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.JobSuggestionCacheRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import com.resqora.resqora_backend.service.jobs.JobPosting;
import com.resqora.resqora_backend.service.jobs.JobSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
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
class JobSuggestionIntegrationTest {
    private static final String RESUME_TEXT = "Alex Johnson\nalex@example.com\nSKILLS\nJava, Spring Boot, PostgreSQL, Teamwork\n";

    // Fake job site: no network, returns canned jobs and records calls
    static class FakeJobSearchClient implements JobSearchClient {
        boolean configured = true;
        boolean fail = false;
        int calls = 0;
        List<String> lastKeywords = new ArrayList<>();

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<JobPosting> search(List<String> keywords, int maxResults) {
            calls++;
            lastKeywords = keywords;
            if (fail) {
                throw new IllegalStateException("adzuna down");
            }
            return List.of(
                    new JobPosting("Frontend Developer", "Acme", "Pune", "React and TypeScript", "https://a.example/1"),
                    new JobPosting("Java Backend Engineer", "Beta", "Kolkata", "Java, Spring Boot and PostgreSQL", "https://a.example/2"),
                    new JobPosting("Java Developer", "Gamma", "Bengaluru", "Java and Docker", "https://a.example/3"));
        }
    }

    @TestConfiguration
    static class FakeJobsConfig {
        @Bean
        @Primary
        FakeJobSearchClient fakeJobSearchClient() {
            return new FakeJobSearchClient();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JobSuggestionCacheRepository cacheRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FakeJobSearchClient fakeJobs;

    private User user;
    private Resume resume;
    private String token;

    @BeforeEach
    void setUp() {
        fakeJobs.configured = true;
        fakeJobs.fail = false;
        fakeJobs.calls = 0;
        user = userRepository.save(new User("jobs-" + UUID.randomUUID() + "@example.com", "hash", "Jobs"));
        resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", RESUME_TEXT));
        token = "Bearer " + jwtService.generateToken(user);
    }

    private String jobsUrl() {
        return "/api/resumes/" + resume.getId() + "/jobs";
    }

    @Test
    void returnsJobsSortedByMatchScore() throws Exception {
        mockMvc.perform(get(jobsUrl()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.jobs.length()").value(3))
                .andExpect(jsonPath("$.jobs[0].title").value("Java Backend Engineer"))
                .andExpect(jsonPath("$.jobs[0].company").value("Beta"))
                .andExpect(jsonPath("$.jobs[0].location").value("Kolkata"))
                .andExpect(jsonPath("$.jobs[0].matchScore").value(100))
                .andExpect(jsonPath("$.jobs[0].url").value("https://a.example/2"))
                .andExpect(jsonPath("$.jobs[1].title").value("Java Developer"))
                .andExpect(jsonPath("$.jobs[1].matchScore").value(50))
                .andExpect(jsonPath("$.jobs[1].matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.jobs[2].matchScore").value(0));
        assertEquals(List.of("Java", "Spring Boot", "Spring"), fakeJobs.lastKeywords);
    }

    @Test
    void secondRequestIsServedFromCache() throws Exception {
        mockMvc.perform(get(jobsUrl()).header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get(jobsUrl()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.jobs[0].title").value("Java Backend Engineer"));
        assertEquals(1, fakeJobs.calls);
    }

    @Test
    void expiredCacheIsRefreshed() throws Exception {
        mockMvc.perform(get(jobsUrl()).header("Authorization", token)).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE job_suggestions SET created_at = DATEADD('HOUR', -13, CURRENT_TIMESTAMP) WHERE resume_id = ?",
                resume.getId());

        mockMvc.perform(get(jobsUrl()).header("Authorization", token))
                .andExpect(jsonPath("$.cached").value(false));
        assertEquals(2, fakeJobs.calls);
    }

    @Test
    void otherUsersResumeReturns404() throws Exception {
        User other = userRepository.save(new User("jobs-other-" + UUID.randomUUID() + "@example.com", "hash"));
        mockMvc.perform(get(jobsUrl()).header("Authorization", "Bearer " + jwtService.generateToken(other)))
                .andExpect(status().isNotFound());
        assertEquals(0, fakeJobs.calls);
    }

    @Test
    void missingKeysReturns503() throws Exception {
        fakeJobs.configured = false;
        mockMvc.perform(get(jobsUrl()).header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Job suggestions not available right now"));
        assertEquals(0, fakeJobs.calls);
    }

    @Test
    void clientFailureReturns503AndIsNotCached() throws Exception {
        fakeJobs.fail = true;
        mockMvc.perform(get(jobsUrl()).header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Job suggestions not available right now"));
        assertTrue(cacheRepository.findByResumeId(resume.getId()).isEmpty());
    }

    @Test
    void resumeWithoutKnownSkillsReturnsEmptyList() throws Exception {
        Resume noSkills = resumeRepository.save(new Resume(user, "plain.pdf", "PDF", "Alex\nSKILLS\nTeamwork, Patience\n"));
        mockMvc.perform(get("/api/resumes/" + noSkills.getId() + "/jobs").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(0))
                .andExpect(jsonPath("$.cached").value(false));
        assertEquals(0, fakeJobs.calls);
    }

    @Test
    void deletingResumeDeletesCachedSuggestions() throws Exception {
        mockMvc.perform(get(jobsUrl()).header("Authorization", token)).andExpect(status().isOk());
        assertTrue(cacheRepository.findByResumeId(resume.getId()).isPresent());

        mockMvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());
        assertFalse(cacheRepository.findByResumeId(resume.getId()).isPresent());
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(get(jobsUrl())).andExpect(status().isUnauthorized());
    }
}
