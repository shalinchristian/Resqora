package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeAnalysisRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyseOnUploadIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeAnalysisRepository resumeAnalysisRepository;
    @Autowired private JwtService jwtService;

    private static byte[] pdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(14);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void uploadSavesAnalysisAndListShowsScoreImmediately() throws Exception {
        User user = userRepository.save(new User("upload-" + UUID.randomUUID() + "@example.com", "hash", "Up"));
        String token = "Bearer " + jwtService.generateToken(user);
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf",
                pdf("Alex Johnson", "alex@example.com", "SKILLS", "Java, Spring Boot, PostgreSQL"));

        String body = mockMvc.perform(multipart("/api/resumes/upload").file(file).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overallScore").isNumber())
                .andReturn().getResponse().getContentAsString();
        int resumeId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        int score = com.jayway.jsonpath.JsonPath.read(body, "$.overallScore");

        assertEquals(score, resumeAnalysisRepository.findByResumeId((long) resumeId).orElseThrow().getOverallScore());

        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.resumes[0].id").value(resumeId))
                .andExpect(jsonPath("$.resumes[0].overallScore").value(score));

        mockMvc.perform(get("/api/resumes/" + resumeId + "/analysis").header("Authorization", token))
                .andExpect(jsonPath("$.overallScore").value(score));
    }
}
