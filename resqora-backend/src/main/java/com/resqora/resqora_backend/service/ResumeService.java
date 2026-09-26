package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.service.extraction.DocumentTextExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class ResumeService {
    private final DocumentTextExtractionService documentTextExtractionService;

    public ResumeService(DocumentTextExtractionService documentTextExtractionService) {
        this.documentTextExtractionService = documentTextExtractionService;
    }

    public ResumeUploadResponse uploadResume(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String fileName = file.getOriginalFilename();
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File size must not exceed 5 MB"
            );
        }
        if (fileName == null ||
            !(fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ||
                fileName.toLowerCase(Locale.ROOT).endsWith(".docx"))) {

            throw new IllegalArgumentException(
                    "Only PDF and DOCX files are supported"
            );
        }


        String extractedText = documentTextExtractionService.extract(file);

        return new ResumeUploadResponse("Resume uploaded successfully", fileName, extractedText);
    }
}
