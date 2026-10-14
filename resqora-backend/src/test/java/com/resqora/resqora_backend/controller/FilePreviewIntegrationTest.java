package com.resqora.resqora_backend.controller;

import com.jayway.jsonpath.JsonPath;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Slice 15: original file stored at upload, served only to the owner with safe headers, removed on delete
@SpringBootTest
@AutoConfigureMockMvc
class FilePreviewIntegrationTest {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired private MockMvc mockMvc;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.resqora.resqora_backend.service.JwtService jwtService;

    private String newUser() throws Exception {
        String email = "file-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"abc12345\",\"name\":\"Asha\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"abc12345\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private static byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Asha Rao asha@example.com SKILLS Java Spring Boot PostgreSQL");
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String p : new String[] {"Asha Rao", "asha@example.com", "SKILLS", "Java, Spring Boot"}) {
                doc.createParagraph().createRun().setText(p);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private long upload(String token, String name, String sentType, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", name, sentType, bytes)).header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void pdfIsServedToOwnerWithSafeHeaders() throws Exception {
        String token = newUser();
        byte[] bytes = pdf();
        // the browser-sent type is ignored: it comes from the validated extension
        long id = upload(token, "cv.pdf", "text/html", bytes);
        byte[] served = mockMvc.perform(get("/api/resumes/" + id + "/file").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(bytes, served);
        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.resumes[0].hasFile").value(true));
    }

    @Test
    void docxIsServedWithDocxType() throws Exception {
        String token = newUser();
        long id = upload(token, "cv.docx", "application/octet-stream", docx());
        mockMvc.perform(get("/api/resumes/" + id + "/file").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", DOCX_TYPE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void otherUserMissingResumeAndNoTokenAreRefused() throws Exception {
        String owner = newUser();
        long id = upload(owner, "cv.pdf", "application/pdf", pdf());
        mockMvc.perform(get("/api/resumes/" + id + "/file").header("Authorization", newUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes/999999/file").header("Authorization", owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes/" + id + "/file")).andExpect(status().isUnauthorized());
    }

    @Test
    void resumeWithoutStoredFileIs404AndHasFileFalse() throws Exception {
        // like a resume uploaded before V8: text only
        User user = userRepository.save(new User("old-" + UUID.randomUUID() + "@example.com", "hash"));
        String token = "Bearer " + jwtService.generateToken(user);
        Resume old = resumeRepository.save(new Resume(user, "old.pdf", "PDF", "Asha\nSKILLS\nJava"));
        mockMvc.perform(get("/api/resumes/" + old.getId() + "/file").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes").header("Authorization", token))
                .andExpect(jsonPath("$.resumes[0].hasFile").value(false));
    }

    @Test
    void uploadOverTwoMegabytesIs413() throws Exception {
        mockMvc.perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[2 * 1024 * 1024 + 1]))
                        .header("Authorization", newUser()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.message").value("File size must not exceed 2 MB"));
    }

    @Test
    void deletingResumeOrAccountRemovesStoredFile() throws Exception {
        String token = newUser();
        long first = upload(token, "a.pdf", "application/pdf", pdf());
        long second = upload(token, "b.docx", DOCX_TYPE, docx());

        mockMvc.perform(delete("/api/resumes/" + first).header("Authorization", token)).andExpect(status().isNoContent());
        assertFalse(resumeRepository.findById(first).isPresent());
        mockMvc.perform(get("/api/resumes/" + first + "/file").header("Authorization", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/auth/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"abc12345\"}"))
                .andExpect(status().isNoContent());
        assertFalse(resumeRepository.findById(second).isPresent());
    }
}
