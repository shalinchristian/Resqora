package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BuilderExportIntegrationTest {
    private static final String VALID = """
            {"templateId":"combination","name":"Priya Sharma","email":"priya@example.com",
             "phone":"+91 91234 56789","location":"Gurugram","summary":"Data analyst.",
             "skills":["SQL","Power BI"],
             "experience":[{"company":"Freshcart","title":"Data Analyst","dates":"2024 – Present",
                            "description":"Built dashboards\\nAutomated reports"}],
             "education":[{"institution":"GGSIPU","degree":"B.Tech IT","dates":"2020 – 2024"}],
             "projects":[{"name":"Churn Analysis","description":"Logistic regression"}],
             "certifications":[]}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private String token;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("builder-" + UUID.randomUUID() + "@example.com", "hash"));
        token = "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void exportsDocxAttachment() throws Exception {
        byte[] body = mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"priya-sharma-resume.docx\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(body));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            int skills = text.indexOf("\nSKILLS\n");
            assertTrue(skills > 0 && skills < text.indexOf("\nEXPERIENCE\n"), text);
            assertTrue(text.contains("Built dashboards"));
            assertFalse(text.contains("CERTIFICATIONS"));
        }
    }

    @Test
    void unknownTemplateIs400() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("\"combination\"", "\"modern\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("templateId")));
    }

    @Test
    void missingTemplateIs400() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Priya Sharma\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("templateId")));
    }

    @Test
    void blankNameIs400() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"classic\",\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    void missingNameIs400() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"classic\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void minimalRequestWithOnlyTemplateAndNameWorks() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"header-band\",\"name\":\"Arjun Mehta\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"arjun-mehta-resume.docx\""));
    }

    @Test
    void withoutJwtIs401() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withInvalidJwtIs401() throws Exception {
        mockMvc.perform(post("/api/builder/export")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsExposesContentDisposition() throws Exception {
        mockMvc.perform(options("/api/builder/export")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/builder/export")
                        .header("Origin", "http://localhost:5173")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")));
    }
}
