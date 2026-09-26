package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.exception.GlobalExceptionHandler;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.service.extraction.DocxTextExtractor;
import com.resqora.resqora_backend.service.extraction.DocumentTextExtractionService;
import com.resqora.resqora_backend.service.extraction.PdfTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocumentTextExtractionService extractionService = new DocumentTextExtractionService(
                new PdfTextExtractor(), new DocxTextExtractor());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ResumeController(new ResumeService(extractionService)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/resumes/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Resume file is required"));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File cannot be empty"));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "resume".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF and DOCX files are supported"));
    }

    @Test
    void rejectsFilesLargerThanFiveMegabytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File size must not exceed 5 MB"));
    }

    @Test
    void acceptsPdfAndReturnsFileName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", createPdf("Resume text"));

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("resume.pdf"))
                .andExpect(jsonPath("$.extractedText").value(org.hamcrest.Matchers.containsString("Resume text")));
    }

    @Test
    void rejectsMalformedPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "not a pdf".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unable to extract text from PDF"));
    }

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}