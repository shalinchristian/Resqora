package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.ResumeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    @PostMapping("/upload")
    public ResumeUploadResponse uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return resumeService.uploadResume(file, authentication);
    }

    @GetMapping("/{resumeId}/parsed")
    public ParsedResume getParsedResume(
            @PathVariable Long resumeId,
            Authentication authentication) {
        return resumeService.getParsedResume(resumeId, authentication);
    }

    @GetMapping("/{resumeId}/analysis")
    public AnalysisResult getAnalysis(
            @PathVariable Long resumeId,
            Authentication authentication) {
        return resumeService.getAnalysis(resumeId, authentication);
    }
}
