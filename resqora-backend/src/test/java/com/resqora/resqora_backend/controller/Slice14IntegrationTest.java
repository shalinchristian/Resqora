package com.resqora.resqora_backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.resqora.resqora_backend.demo.DemoDataSeeder;
import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Slice 14: demo protection + reset, Big B's open bugs (B6, B9-B13), unreadable-layout topFinding
@SpringBootTest(properties = {"resqora.demo.enabled=true", "resqora.login.max-attempts-per-email=2"})
@AutoConfigureMockMvc
class Slice14IntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private AiUsageRepository aiUsageRepository;
    @Autowired private DemoDataSeeder seeder;

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String token(ResultActions login) throws Exception {
        return JsonPath.read(login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String newUser() throws Exception {
        String email = "s14-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"abc12345\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated());
        return token(login(email, "abc12345"));
    }

    private String demoToken() throws Exception {
        seeder.seed();
        return token(login(DemoDataSeeder.DEMO_EMAIL, "Demo1234"));
    }

    private MockMultipartFile docx(String... paragraphs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return new MockMultipartFile("file", "r.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", out.toByteArray());
        }
    }

    private long upload(String token, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/resumes/upload").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractedText").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void demoAccountCannotBeChanged() throws Exception {
        String token = demoToken();
        User demo = userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        Long resumeId = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(demo.getId()).get(0).getId();
        String auth = "Bearer " + token;
        mockMvc.perform(delete("/api/resumes/" + resumeId).header("Authorization", auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The demo account can't be changed"));
        mockMvc.perform(delete("/api/auth/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Demo1234\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/auth/password").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Demo1234\",\"newPassword\":\"Other1234\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/auth/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hacked\"}")).andExpect(status().isForbidden());
        assertEquals(3, resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(demo.getId()).size());
        login(DemoDataSeeder.DEMO_EMAIL, "Demo1234").andExpect(status().isOk());
    }

    @Test
    void ordinaryUsersAreNotAffectedByDemoMode() throws Exception {
        String token = newUser();
        long id = upload(token, docx("Asha Rao", "asha@x.com", "SKILLS", "Java, SQL"));
        mockMvc.perform(delete("/api/resumes/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void startupSeedResetsDemoData() throws Exception {
        String token = demoToken();
        User demo = userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        upload(token, docx("Extra", "extra@x.com", "SKILLS", "Go"));
        aiUsageRepository.save(new AiUsage(demo));
        assertEquals(4, resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(demo.getId()).size());

        seeder.seed();

        List<Resume> resumes = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(demo.getId());
        assertEquals(3, resumes.size());
        assertEquals(0, aiUsageRepository.countByUserIdAndCreatedAtGreaterThanEqual(demo.getId(), Instant.EPOCH));
        assertEquals(demo.getId(), userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow().getId());
    }

    @Test
    void unreadableLayoutIsTheTopFinding() throws Exception {
        String token = newUser();
        String prose = "This paragraph describes many things in a layout that the parser cannot split into sections. ";
        long id = upload(token, docx("Asha Rao", prose.repeat(3), prose.repeat(3), prose.repeat(2)));
        mockMvc.perform(get("/api/resumes/" + id + "/analysis").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[0].category").value(ResumeAnalyzer.LAYOUT_CATEGORY))
                .andExpect(jsonPath("$.findings[0].message").value(ResumeAnalyzer.UNREADABLE_LAYOUT_MESSAGE));
        mockMvc.perform(get("/api/resumes").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.resumes[0].topFinding").value(ResumeAnalyzer.UNREADABLE_LAYOUT_MESSAGE));
    }

    @Test
    void jobsWithNoSkillsIsEmptyWithoutCallingJobicy() throws Exception { // B6 (Jobicy needs no key)
        String token = newUser();
        long id = upload(token, docx("Asha Rao", "asha@x.com", "Just a line of text with nothing else in it at all."));
        mockMvc.perform(get("/api/resumes/" + id + "/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.length()").value(0));
    }

    @Test
    void lockedLoginSendsRetryAfter() throws Exception { // B9
        String email = "s14-lock-" + UUID.randomUUID() + "@example.com";
        login(email, "wrong1").andExpect(status().isUnauthorized());
        login(email, "wrong2").andExpect(status().isUnauthorized());
        String retry = login(email, "wrong3").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andReturn().getResponse().getHeader("Retry-After");
        long seconds = Long.parseLong(retry);
        assertEquals(true, seconds > 0 && seconds <= 900, "Retry-After " + seconds);
    }

    @Test
    void controlCharactersInNamesAreRejected() throws Exception { // B11
        String token = newUser();
        mockMvc.perform(patch("/api/auth/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\\u0000b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("control characters")));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ctl-" + UUID.randomUUID() + "@x.com\",\"password\":\"abc12345\",\"name\":\"a\\nb\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oldAiEndpointIsGoneAndBadChatRoleIs400() throws Exception { // B12, updated in slice 15
        String token = newUser();
        long id = upload(token, docx("Asha Rao", "asha@x.com", "SKILLS", "Java"));
        mockMvc.perform(post("/api/resumes/" + id + "/ai").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"FOO\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/resumes/" + id + "/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"history\":[{\"role\":\"system\",\"text\":\"x\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must be user or model")));
    }

    @Test
    void wrongMethodOnPublicAuthEndpointsIs405() throws Exception { // B13
        mockMvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(get("/api/auth/register")).andExpect(status().isMethodNotAllowed());
    }
}
