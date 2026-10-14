package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.demo.DemoAccountGuard;
import com.resqora.resqora_backend.dto.resume.ResumeListResponse;
import com.resqora.resqora_backend.dto.resume.ResumeSummary;
import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.ResumeAnalysis;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.exception.FileTooLargeException;
import com.resqora.resqora_backend.exception.ResumeLimitExceededException;
import com.resqora.resqora_backend.exception.ScannedDocumentException;
import org.springframework.transaction.support.TransactionTemplate;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Locale;

@Service
public class ResumeService {
    public static final int MAX_RESUMES_PER_USER = 5;
    public static final String CONTENT_TYPE_PDF = "application/pdf";
    public static final String CONTENT_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DocumentTextExtractionService documentTextExtractionService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeParser resumeParser;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final TransactionTemplate transactionTemplate;
    private final DemoAccountGuard demoAccountGuard;

    // without the demo guard (unit tests): demo mode off
    public ResumeService(
            DocumentTextExtractionService documentTextExtractionService,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ResumeParser resumeParser,
            ResumeAnalyzer resumeAnalyzer,
            ResumeAnalysisRepository resumeAnalysisRepository,
            TransactionTemplate transactionTemplate) {
        this(documentTextExtractionService, resumeRepository, userRepository, resumeParser, resumeAnalyzer,
                resumeAnalysisRepository, transactionTemplate, new DemoAccountGuard(false));
    }

    @Autowired
    public ResumeService(
            DocumentTextExtractionService documentTextExtractionService,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ResumeParser resumeParser,
            ResumeAnalyzer resumeAnalyzer,
            ResumeAnalysisRepository resumeAnalysisRepository,
            TransactionTemplate transactionTemplate,
            DemoAccountGuard demoAccountGuard) {
        this.demoAccountGuard = demoAccountGuard;
        this.documentTextExtractionService = documentTextExtractionService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.resumeParser = resumeParser;
        this.resumeAnalyzer = resumeAnalyzer;
        this.resumeAnalysisRepository = resumeAnalysisRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public ResumeUploadResponse uploadResume(MultipartFile file, Authentication authentication) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String fileName = file.getOriginalFilename();
        if (file.getSize() > Resume.MAX_FILE_BYTES) {
            throw new FileTooLargeException();
        }
        if (fileName == null ||
            !(fileName.toLowerCase(Locale.ROOT).endsWith(".pdf") ||
                fileName.toLowerCase(Locale.ROOT).endsWith(".docx"))) {

            throw new IllegalArgumentException(
                    "Only PDF and DOCX files are supported"
            );
        }


        Long userId = (Long) authentication.getPrincipal();
        // check the limit first, text extraction is slower
        if (resumeRepository.countByUserId(userId) >= MAX_RESUMES_PER_USER) {
            throw new ResumeLimitExceededException(MAX_RESUMES_PER_USER);
        }

        String extractedText = documentTextExtractionService.extract(file);
        String documentType = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        // keep the original bytes for the preview. content type comes from the already checked extension
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }
        String contentType = "PDF".equals(documentType) ? CONTENT_TYPE_PDF : CONTENT_TYPE_DOCX;
        rejectIfNoText(extractedText, documentType);
        // lock the user row, recount and insert in one transaction so parallel uploads can't go over 5
        Resume savedResume = transactionTemplate.execute(status -> {
            User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
            if (resumeRepository.countByUserId(userId) >= MAX_RESUMES_PER_USER) {
                throw new ResumeLimitExceededException(MAX_RESUMES_PER_USER);
            }
            return resumeRepository.save(new Resume(user, fileName, documentType, extractedText, fileBytes, contentType));
        });

        // analyse right away so the dashboard has a score straight away.
        // the upload already worked, so an analysis error shouldn't fail it.
        // GET /analysis just tries again later
        Integer overallScore = null;
        try {
            overallScore = analyzeIfMissing(savedResume).overallScore();
        } catch (RuntimeException exception) {
            log.warn("Analysis after upload failed for resume {}: {}", savedResume.getId(), exception.toString());
        }

        return new ResumeUploadResponse(
            savedResume.getId(),
            "Resume uploaded successfully",
            fileName,
            documentType,
            overallScore);
    }

    static final int MIN_TEXT_CHARACTERS = 20;

    // a scanned (image only) PDF gives no text, reject it before saving anything
    private void rejectIfNoText(String extractedText, String documentType) {
        long visible = extractedText == null ? 0 : extractedText.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        if (visible < MIN_TEXT_CHARACTERS) {
            throw new ScannedDocumentException("PDF".equals(documentType)
                    ? "This looks like a scanned PDF. Please upload a text-based PDF or DOCX."
                    : "This document has no readable text. Please upload a text-based PDF or DOCX.");
        }
    }

    public ResumeListResponse listResumes(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Resume> ownResumes = resumeRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        // saved scores for all listed resumes in one query. null means not analysed yet
        Map<Long, ResumeAnalysis> analyses = ownResumes.isEmpty() ? Map.of()
                : resumeAnalysisRepository.findByResumeIdIn(ownResumes.stream().map(Resume::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(a -> a.getResume().getId(), a -> a));
        List<ResumeSummary> resumes = ownResumes.stream()
                .map(resume -> new ResumeSummary(
                        resume.getId(),
                        resume.getOriginalFileName(),
                        resume.getDocumentType(),
                        resume.getCreatedAt(),
                        analyses.containsKey(resume.getId()) ? analyses.get(resume.getId()).getOverallScore() : null,
                        analyses.containsKey(resume.getId()) ? topFinding(analyses.get(resume.getId())) : null,
                        resume.hasFile()))
                .toList();
        return new ResumeListResponse(resumes.size(), MAX_RESUMES_PER_USER, resumes);
    }

    public void deleteResume(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        demoAccountGuard.rejectIfDemo(resume.getUser());
        resumeRepository.delete(resume);
    }

    public ParsedResume getParsedResume(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        return resumeParser.parse(resume.getExtractedText());
    }

    public AnalysisResult getAnalysis(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        // return the saved result if we already analysed it, otherwise compute once and save
        return analyzeIfMissing(resume);
    }

    // shared by upload and GET /analysis: return the saved result, or compute and save it once
    private AnalysisResult analyzeIfMissing(Resume resume) {
        return resumeAnalysisRepository.findByResumeId(resume.getId())
                .map(this::toAnalysisResult)
                .orElseGet(() -> analyzeAndSave(resume));
    }

    private AnalysisResult analyzeAndSave(Resume resume) {
        ParsedResume parsedResume = resumeParser.parse(resume.getExtractedText());
        AnalysisResult result = resumeAnalyzer.analyze(parsedResume, resume.getExtractedText());
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

    // picked from the saved findings, no re-analysis: unreadable-layout warning first,
    // then a WARNING tied to a line, then any WARNING, else null
    static String topFinding(ResumeAnalysis saved) {
        List<AnalysisFinding> findings;
        try {
            findings = JSON.readValue(saved.getFindingsJson(), new TypeReference<List<AnalysisFinding>>() { });
        } catch (RuntimeException exception) {
            return null;
        }
        return findings.stream()
                .filter(f -> ResumeAnalyzer.LAYOUT_CATEGORY.equals(f.category()))
                .findFirst()
                .or(() -> findings.stream().filter(f -> "WARNING".equals(f.type()) && f.line() != null).findFirst())
                .or(() -> findings.stream().filter(f -> "WARNING".equals(f.type())).findFirst())
                .map(AnalysisFinding::message)
                .orElse(null);
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

    // original file for the preview. 404 if it's not theirs, not stored, or not pdf/docx
    public Resume getOwnedFile(Long resumeId, Authentication authentication) {
        Resume resume = findOwnedResume(resumeId, authentication);
        if (!resume.hasFile() || !(CONTENT_TYPE_PDF.equals(resume.getContentType())
                || CONTENT_TYPE_DOCX.equals(resume.getContentType()))) {
            throw new ResumeNotFoundException();
        }
        return resume;
    }

    public Resume findOwnedResume(Long resumeId, Authentication authentication) {
        Long authenticatedUserId = (Long) authentication.getPrincipal();
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(ResumeNotFoundException::new);

        if (!resume.getUser().getId().equals(authenticatedUserId)) {
            throw new ResumeNotFoundException();
        }
        return resume;
    }
}
