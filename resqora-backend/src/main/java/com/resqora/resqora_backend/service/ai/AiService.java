package com.resqora.resqora_backend.service.ai;

import com.resqora.resqora_backend.dto.ai.AiAction;
import com.resqora.resqora_backend.dto.ai.AiRequest;
import com.resqora.resqora_backend.dto.ai.AiResponse;
import com.resqora.resqora_backend.entity.AiCachedResponse;
import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.exception.AiDailyLimitExceededException;
import com.resqora.resqora_backend.exception.AiUnavailableException;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.repository.AiCachedResponseRepository;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.ResumeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class AiService {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final AiCachedResponseRepository cacheRepository;
    private final AiUsageRepository usageRepository;
    private final AiClient aiClient;
    private final int dailyLimit;
    private final int dailyCap;

    public AiService(
            ResumeService resumeService,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            AiCachedResponseRepository cacheRepository,
            AiUsageRepository usageRepository,
            AiClient aiClient,
            @Value("${resqora.ai.daily-limit:10}") int dailyLimit,
            @Value("${resqora.ai.daily-cap:50}") int dailyCap) {
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.cacheRepository = cacheRepository;
        this.usageRepository = usageRepository;
        this.aiClient = aiClient;
        this.dailyLimit = dailyLimit;
        this.dailyCap = dailyCap;
    }

    public AiResponse run(Long resumeId, AiRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        // Ownership check (404 for missing / someone else's resume) is done inside ResumeService
        ParsedResume parsedResume = resumeService.getParsedResume(resumeId, authentication);

        String jobDescription = request.jobDescription() == null ? "" : request.jobDescription().trim();
        if (request.action() == AiAction.SHOULD_I_APPLY && jobDescription.isEmpty()) {
            throw new IllegalArgumentException("jobDescription is required for SHOULD_I_APPLY");
        }
        String jdHash = sha256(jobDescription);

        // 1. Same question asked before? Return the saved answer: no AI call, no quota used.
        var cached = cacheRepository.findByResumeIdAndActionAndJobDescriptionHash(
                resumeId, request.action().name(), jdHash);
        if (cached.isPresent()) {
            return toResponse(request.action(), cached.get().getResponseJson(), remainingToday(userId));
        }

        // 2. Real AI call needed: check configuration, the user's limit and the app-wide cap first.
        if (!aiClient.isConfigured()) {
            throw new AiUnavailableException();
        }
        long usedToday = usedToday(userId);
        if (usedToday >= dailyLimit) {
            throw new AiDailyLimitExceededException();
        }
        if (usageRepository.countByCreatedAtGreaterThanEqual(startOfToday()) >= dailyCap) {
            // protects the shared Gemini free-tier quota for the whole app
            throw new AiUnavailableException();
        }

        AnalysisResult analysis = resumeService.getAnalysis(resumeId, authentication);
        String answerJson = aiClient.generateJson(buildPrompt(request.action(), parsedResume, analysis, jobDescription));
        String normalized = normalize(request.action(), answerJson);

        usageRepository.save(new AiUsage(userRepository.getReferenceById(userId)));
        cacheRepository.save(new AiCachedResponse(
                resumeRepository.getReferenceById(resumeId), request.action().name(), jdHash, normalized));

        return toResponse(request.action(), normalized, (int) Math.max(0, dailyLimit - usedToday - 1));
    }

    private static Instant startOfToday() {
        return LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private long usedToday(Long userId) {
        return usageRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, startOfToday());
    }

    private int remainingToday(Long userId) {
        return (int) Math.max(0, dailyLimit - usedToday(userId));
    }

    // Parses the AI's JSON and keeps only what we expect, in a fixed shape: {"text": ..., "items": [...]}
    private String normalize(AiAction action, String answerJson) {
        try {
            JsonNode root = JSON.readTree(answerJson);
            String text = root.path("text").isString() ? root.path("text").asString().trim() : null;
            List<String> items = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                if (item.isString() && !item.asString().isBlank()) {
                    items.add(item.asString().trim());
                }
            }
            if (action == AiAction.INTERVIEW_QUESTIONS && items.size() > 5) {
                items = items.subList(0, 5);
            }
            boolean usable = action == AiAction.SHOULD_I_APPLY ? text != null && !text.isEmpty() : !items.isEmpty();
            if (!usable) {
                throw new AiUnavailableException();
            }
            Map<String, Object> clean = new java.util.LinkedHashMap<>();
            clean.put("text", action == AiAction.SHOULD_I_APPLY ? text : null);
            clean.put("items", action == AiAction.SHOULD_I_APPLY ? List.of() : items);
            return JSON.writeValueAsString(clean);
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiUnavailableException(exception);
        }
    }

    private AiResponse toResponse(AiAction action, String json, int remaining) {
        JsonNode root = JSON.readTree(json);
        String text = root.path("text").isString() ? root.path("text").asString() : null;
        List<String> items = new ArrayList<>();
        root.path("items").forEach(item -> items.add(item.asString()));
        return new AiResponse(action, text, List.copyOf(items), remaining);
    }

    private String buildPrompt(AiAction action, ParsedResume resume, AnalysisResult analysis, String jobDescription) {
        String task = switch (action) {
            case INTERVIEW_QUESTIONS -> """
                    Write exactly 5 interview questions an interviewer is likely to ask this candidate,
                    based on their skills, projects and experience%s.
                    Reply as JSON: {"items": ["question 1", "question 2", "question 3", "question 4", "question 5"]}
                    """.formatted(jobDescription.isEmpty() ? "" : " and the job description");
            case SHOULD_I_APPLY -> """
                    Decide if this candidate should apply for the job description below.
                    Start "text" with exactly one verdict word: YES, MAYBE or NO, then a colon and a short reason
                    (max 3 sentences) mentioning the most important matching and missing skills.
                    Reply as JSON: {"text": "YES: ..."}
                    """;
            case REWRITE_WEAK_POINTS -> """
                    Using the analysis findings, pick the weakest parts of this resume and rewrite them.
                    Give 3 to 5 items; each item is one improved, ready-to-paste resume line or bullet.
                    Do not invent employers, degrees or numbers that are not in the resume.
                    Reply as JSON: {"items": ["...", "..."]}
                    """;
        };
        return """
                You are a helpful career assistant for a student. Reply ONLY with JSON, no markdown.
                %s
                RESUME (parsed, JSON):
                %s
                RULE-BASED ANALYSIS (JSON):
                %s
                JOB DESCRIPTION:
                %s
                """.formatted(task, JSON.writeValueAsString(resume), JSON.writeValueAsString(analysis),
                jobDescription.isEmpty() ? "(none given)" : jobDescription);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
