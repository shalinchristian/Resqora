package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.exception.ResumeNotFoundException;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.extraction.DocumentTextExtractionService;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class ResumeService {
    private final DocumentTextExtractionService documentTextExtractionService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeParser resumeParser;

    public ResumeService(
            DocumentTextExtractionService documentTextExtractionService,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ResumeParser resumeParser) {
        this.documentTextExtractionService = documentTextExtractionService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.resumeParser = resumeParser;
    }

    public ResumeUploadResponse uploadResume(MultipartFile file, Authentication authentication) {

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
        String documentType = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        Long userId = (Long) authentication.getPrincipal();
        User user = userRepository.findById(userId)
            .orElseThrow(AuthenticatedUserNotFoundException::new);
        Resume savedResume = resumeRepository.save(
            new Resume(user, fileName, documentType, extractedText));

        return new ResumeUploadResponse(
            savedResume.getId(),
            "Resume uploaded successfully",
            fileName,
            documentType,
            extractedText);
    }

    public ParsedResume getParsedResume(Long resumeId, Authentication authentication) {
        Long authenticatedUserId = (Long) authentication.getPrincipal();
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(ResumeNotFoundException::new);

        if (!resume.getUser().getId().equals(authenticatedUserId)) {
            throw new ResumeNotFoundException();
        }

        return resumeParser.parse(resume.getExtractedText());
    }
}
