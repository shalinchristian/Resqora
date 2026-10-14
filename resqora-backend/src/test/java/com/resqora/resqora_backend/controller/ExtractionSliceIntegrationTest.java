package com.resqora.resqora_backend.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice 12: Big B's scorecard fixes that need the full app (security, DB, upload pipeline)
@SpringBootTest
@AutoConfigureMockMvc
class ExtractionSliceIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JwtService jwtService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("s12-" + UUID.randomUUID() + "@example.com", "hash"));
        token = "Bearer " + jwtService.generateToken(user);
    }

    private MockMultipartFile sample(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/samples/" + name)) {
            return new MockMultipartFile("file", name, "application/octet-stream", in.readAllBytes());
        }
    }

    private String registerJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"Ravi\"}";
    }

    @Test
    void errorPageIsPublicAndNotA401() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    void uploadWithJsonBodyIs400NotA401() throws Exception {
        mockMvc.perform(post("/api/resumes/upload").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        String local = "Case-" + UUID.randomUUID();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("  " + local + "@Example.COM ")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(local.toLowerCase() + "@example.com")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + local.toUpperCase() + "@EXAMPLE.COM\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void parallelDuplicateRegistrationsGiveOne201AndThe409s() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        List<Integer> statuses = runInParallel(8, () -> mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(registerJson(email)))
                .andReturn().getResponse().getStatus());
        assertEquals(1, statuses.stream().filter(s -> s == 201).count());
        assertEquals(7, statuses.stream().filter(s -> s == 409).count());
    }

    @Test
    void scannedPdfIs422AndUsesNoSlot() throws Exception {
        mockMvc.perform(multipart("/api/resumes/upload").file(sample("04_scanned_image_only.pdf"))
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message")
                        .value("This looks like a scanned PDF. Please upload a text-based PDF or DOCX."));
        assertEquals(0, resumeRepository.countByUserId(user.getId()));
    }

    @Test
    void listCarriesTopFindingFromSavedAnalysis() throws Exception {
        mockMvc.perform(multipart("/api/resumes/upload").file(sample("06_bullets_wrapped.pdf"))
                        .header("Authorization", token))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.resumes[0].topFinding").value(containsString("Capgemini")));
    }

    @Test
    void parallelUploadsCannotPassFiveSlots() throws Exception {
        MockMultipartFile file = sample("01_baseline.pdf");
        List<Integer> statuses = runInParallel(8, () -> mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file).header("Authorization", token))
                .andReturn().getResponse().getStatus());
        assertEquals(5, statuses.stream().filter(s -> s == 201).count());
        assertEquals(3, statuses.stream().filter(s -> s == 409).count());
        assertEquals(5, resumeRepository.countByUserId(user.getId()));
    }

    @Test
    void deletedUserTokenIs401OnResumeEndpoints() throws Exception {
        userRepository.delete(user);
        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/resumes/1/jobs").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    private List<Integer> runInParallel(int count, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdown();
        }
    }
}
