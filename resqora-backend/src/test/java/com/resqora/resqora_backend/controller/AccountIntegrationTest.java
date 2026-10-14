package com.resqora.resqora_backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.resqora.resqora_backend.demo.DemoDataSeeder;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeAnalysisRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.ResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private ResumeAnalysisRepository analysisRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ResumeService resumeService;
    @Autowired private com.resqora.resqora_backend.repository.AiUsageRepository aiUsageRepository;
    @Autowired private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private String email;

    private String registerAndLogin(String password) throws Exception {
        email = "acct-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated());
        return login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()
                .transform(body -> JsonPath.<String>read(body, "$.token"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions changePassword(String token, String body) throws Exception {
        return mockMvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteMe(String token, String body) throws Exception {
        return mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void changePasswordReturns204AndNewPasswordWorks() throws Exception {
        String token = registerAndLogin("password123");
        changePassword(token, "{\"currentPassword\":\"password123\",\"newPassword\":\"newpass456\"}")
                .andExpect(status().isNoContent());
        login(email, "password123").andExpect(status().isUnauthorized());
        login(email, "newpass456").andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIs403NotLogout() throws Exception {
        String token = registerAndLogin("password123");
        changePassword(token, "{\"currentPassword\":\"wrong1234\",\"newPassword\":\"newpass456\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void weakNewPasswordIs400() throws Exception {
        String token = registerAndLogin("password123");
        changePassword(token, "{\"currentPassword\":\"password123\",\"newPassword\":\"short1\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("newPassword:")));
        changePassword(token, "{\"currentPassword\":\"password123\",\"newPassword\":\"onlyletters\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("newPassword:")));
    }

    @Test
    void samePasswordIs400() throws Exception {
        String token = registerAndLogin("password123");
        changePassword(token, "{\"currentPassword\":\"password123\",\"newPassword\":\"password123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current password"));
    }

    @Test
    void changePasswordWithoutTokenIs401() throws Exception {
        mockMvc.perform(put("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccountRemovesEverythingAndOldTokenGets401() throws Exception {
        String token = registerAndLogin("password123");
        byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/samples/01_baseline.pdf"));
        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        List<Long> resumeIds = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(Resume::getId).toList();
        assertEquals(1, resumeIds.size());
        assertFalse(analysisRepository.findByResumeIdIn(resumeIds).isEmpty());

        deleteMe(token, "{\"password\":\"password123\"}").andExpect(status().isNoContent());

        assertTrue(userRepository.findById(userId).isEmpty());
        assertEquals(0, resumeRepository.countByUserId(userId));
        assertTrue(analysisRepository.findByResumeIdIn(resumeIds).isEmpty());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        login(email, "password123").andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccountWrongPasswordIs403AndKeepsAccount() throws Exception {
        String token = registerAndLogin("password123");
        deleteMe(token, "{\"password\":\"nope12345\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Password is incorrect"));
        assertTrue(userRepository.findByEmail(email).isPresent());
    }

    @Test
    void deleteAccountMissingPasswordIs400() throws Exception {
        String token = registerAndLogin("password123");
        deleteMe(token, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void corsPreflightAllowsPutAndDelete() throws Exception {
        for (String method : List.of("PUT", "DELETE")) {
            mockMvc.perform(options("/api/auth/me").header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
        }
    }

    @Test
    void demoSeederCreatesAnalysedResumesAndIsIdempotent() throws Exception {
        userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).ifPresent(u -> {
            resumeRepository.deleteAll(resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(u.getId()));
            userRepository.delete(u);
        });
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, resumeService, resumeRepository, aiUsageRepository, transactionTemplate, "Demo1234");
        seeder.seed();
        seeder.seed();

        User demo = userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        assertEquals("Demo User", demo.getName());
        List<Long> ids = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(demo.getId()).stream().map(Resume::getId).toList();
        assertEquals(3, ids.size());
        assertEquals(3, analysisRepository.findByResumeIdIn(ids).size());
        login(DemoDataSeeder.DEMO_EMAIL, "Demo1234").andExpect(status().isOk());
    }
}
