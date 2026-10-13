package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.resume.ResumeListResponse;
import com.resqora.resqora_backend.dto.resume.ResumeSummary;
import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.ResumeAnalysis;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.exception.ResumeLimitExceededException;
import com.resqora.resqora_backend.exception.ResumeNotFoundException;
import com.resqora.resqora_backend.model.analysis.AnalysisFinding;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.repository.ResumeAnalysisRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.extraction.DocumentTextExtractionService;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;

@Service
public class ResumeService {
    public static final int MAX_RESUMES_PER_USER = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DocumentTextExtractionService documentTextExtractionService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeParser resumeParser;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ResumeAnalysisRepository resumeAnalysisRepository;

    public ResumeService(
            DocumentTextExtractionService documentTextExtractionService,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ResumeParser resumeParser,
            ResumeAnalyzer resumeAnalyzer,
            ResumeAnalysisRepository resumeAnalysisRepository) {
        this.documentTextExtractionService = documentTextExtractionService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.resumeParser = resumeParser;
        this.resumeAnalyzer = resumeAnalyzer;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
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


        Long userId = (Long) authentication.getPrincipal();
        // Check the limit before the (slower) text extraction
        if (resumeRepository.countByUserId(userId) >= MAX_RESUMES_PER_USER) {
            throw new ResumeLimitExceededException(MAX_RESUMES_PER_USER);
        }

        String extractedText = documentTextExtractionService.extract(file);
        String documentType = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
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

    public ResumeListResponse listResumes(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ResumeSummary> resumes = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(resume -> new ResumeSummary(
                        resume.getId(),
                        resume.getOriginalFileName(),
                        resume.getDocumentType(),
                        resume.getCreatedAt()))
                .toList();
        return new ResumeListResponse(resumes.size(), MAX_RESUMES_PER_USER, resumes);
    }

    public void deleteResume(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        resumeRepository.delete(resume);
    }

    public ParsedResume getParsedResume(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        return resumeParser.parse(resume.getExtractedText());
    }

    public AnalysisResult getAnalysis(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        // Return the saved result if we analysed this resume before; otherwise compute once and save it
        return resumeAnalysisRepository.findByResumeId(resume.getId())
                .map(this::toAnalysisResult)
                .orElseGet(() -> analyzeAndSave(resume));
    }

    private AnalysisResult analyzeAndSave(Resume resume) {
        ParsedResume parsedResume = resumeParser.parse(resume.getExtractedText());
        AnalysisResult result = resumeAnalyzer.analyze(parsedResume);
        resumeAnalysisRepository.save(new ResumeAnalysis(
                resume,
                result.overallScore(),
                result.completenessScore(),
                result.skillsScore(),
                result.projectsScore(),
                result.experienceScore(),
                result.educationScore(),
                JSON.writeValueAsString(result.findings())));
        return result;
    }

    private AnalysisResult toAnalysisResult(ResumeAnalysis saved) {
        List<AnalysisFinding> findings = JSON.readValue(
                saved.getFindingsJson(), new TypeReference<List<AnalysisFinding>>() { });
        return new AnalysisResult(
                saved.getOverallScore(),
                saved.getCompletenessScore(),
                saved.getSkillsScore(),
                saved.getProjectsScore(),
                saved.getExperienceScore(),
                saved.getEducationScore(),
                findings);
    }

    private Resume findOwnedResume(Long resumeId, Authentication authentication) {
        Long authenticatedUserId = (Long) authentication.getPrincipal();
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(ResumeNotFoundException::new);

        if (!resume.getUser().getId().equals(authenticatedUserId)) {
            throw new ResumeNotFoundException();
        }
        return resume;
    }
}
