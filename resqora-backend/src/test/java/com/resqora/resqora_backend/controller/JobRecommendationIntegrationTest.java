package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.*;
import com.resqora.resqora_backend.repository.*;
import com.resqora.resqora_backend.service.JwtService;
import com.resqora.resqora_backend.service.jobs.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class JobRecommendationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;
    @Autowired AiUsageRepository usage;
    @Autowired JwtService jwt;
    @MockitoBean JobsForceClient force;
    @MockitoBean JobSearchClient jobicy;
    private Resume resume;
    private String token;
    @BeforeEach void setup() {
        User user = users.save(new User("picks-" + UUID.randomUUID() + "@example.com", "hash", "Picks"));
        resume = resumes.save(new Resume(user, "resume.pdf", "PDF", "Alex Johnson\nLocation: Pune, India\nSKILLS\nJava, SQL\n"));
        token = "Bearer " + jwt.generateToken(user);
        when(force.search(any())).thenReturn(List.of(new JobsForcePosting("Java Developer", "JobsForce India — confidential client",
                "Pune", "Pune, India", "Java SQL", "https://jobsforceindia.com/job/example", null, LocalDate.of(2026,11,11))));
        when(jobicy.isConfigured()).thenReturn(true);
        when(jobicy.search(anyList(), anyInt())).thenReturn(List.of());
    }
    private String url() { return "/api/resumes/" + resume.getId() + "/job-recommendations"; }
    @Test void jwtOwnerGetsExactResponseShapeWithoutConsumingAiQuota() throws Exception {
        long before = usage.count();
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(1))
                .andExpect(jsonPath("$.jobs[0].title").value("Java Developer"))
                .andExpect(jsonPath("$.jobs[0].hiringName").value("JobsForce India — confidential client"))
                .andExpect(jsonPath("$.jobs[0].location").value("Pune, India"))
                .andExpect(jsonPath("$.jobs[0].why").isString())
                .andExpect(jsonPath("$.jobs[0].url").value("https://jobsforceindia.com/job/example"))
                .andExpect(jsonPath("$.jobs[0].applyUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.jobs[0].postedAt").value("2026-11-11"))
                .andExpect(jsonPath("$.jobs[0].source").value("JobsForce India"));
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isOk());
        verify(force, times(1)).search(any()); assertEquals(before, usage.count());
    }
    @Test void requiresJwt() throws Exception {
        mvc.perform(get(url())).andExpect(status().isUnauthorized()); verifyNoInteractions(force);
    }
    @Test void foreignResumeIs404EvenAfterOwnerPopulatesCache() throws Exception {
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isOk());
        User other = users.save(new User("other-picks-" + UUID.randomUUID() + "@example.com", "hash"));
        mvc.perform(get(url()).header("Authorization", "Bearer " + jwt.generateToken(other))).andExpect(status().isNotFound());
        verify(force, times(1)).search(any());
    }
    @Test void nonexistentResumeIs404AndDoesNotCallProvider() throws Exception {
        mvc.perform(get("/api/resumes/9223372036854775807/job-recommendations").header("Authorization", token))
                .andExpect(status().isNotFound()); verifyNoInteractions(force);
    }
    @Test void jobsForceFailureIs200EmptyAndExistingJobicyStillWorks() throws Exception {
        when(force.search(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("read timeout"));
        when(jobicy.search(anyList(), anyInt())).thenReturn(List.of(new JobPosting("Java Engineer", "Real company", "India", "Java", "https://jobicy.com/jobs/example")));
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isOk()).andExpect(content().json("{\"jobs\":[]}"));
        mvc.perform(get("/api/resumes/" + resume.getId() + "/jobs").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobs[0].source").value("Jobicy"));
    }
    @Test void bothCapsAreFiveAndDedupeUsesActualJobicyFive() throws Exception {
        List<JobPosting> ten = IntStream.range(0, 10).mapToObj(i -> new JobPosting("Java Developer", "Company " + i, "Pune", "Java SQL", "https://jobicy.com/" + i)).toList();
        when(jobicy.search(anyList(), anyInt())).thenReturn(ten);
        when(force.search(any())).thenReturn(IntStream.range(0,10).mapToObj(i -> new JobsForcePosting("Java Developer",
                "Company " + i, "Pune", "Pune", "Java SQL", "https://jobsforceindia.com/" + i, null, LocalDate.of(2026,11,11))).toList());
        mvc.perform(get("/api/resumes/" + resume.getId() + "/jobs").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobs.length()").value(5));
        mvc.perform(get(url()).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(5))
                .andExpect(jsonPath("$.jobs[0].hiringName").value("Company 5"))
                .andExpect(jsonPath("$.jobs[4].hiringName").value("Company 9"));
    }
}
