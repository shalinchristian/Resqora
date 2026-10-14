package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.resume.ResumeListResponse;
import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.entity.Resume;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }
    @GetMapping
    public ResumeListResponse listResumes(Authentication authentication) {
        return resumeService.listResumes(authentication);
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> deleteResume(
            @PathVariable Long resumeId,
            Authentication authentication) {
        resumeService.deleteResume(resumeId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        ResumeUploadResponse response = resumeService.uploadResume(file, authentication);
        // 201 Created + Location of the new resume's analysis
        return ResponseEntity.created(URI.create("/api/resumes/" + response.id() + "/analysis")).body(response);
    }

    @GetMapping("/{resumeId}/parsed")
    public ParsedResume getParsedResume(
            @PathVariable Long resumeId,
            Authentication authentication) {
        return resumeService.getParsedResume(resumeId, authentication);
    }

    // Original upload for the preview. Only PDF/DOCX content types, never sniffed, shown inline.
    @GetMapping("/{resumeId}/file")
    public ResponseEntity<byte[]> getFile(
            @PathVariable Long resumeId,
            Authentication authentication) {
        Resume resume = resumeService.getOwnedFile(resumeId, authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(resume.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .body(resume.getFileData());
    }

    @GetMapping("/{resumeId}/analysis")
    public AnalysisResult getAnalysis(
            @PathVariable Long resumeId,
            Authentication authentication) {
        return resumeService.getAnalysis(resumeId, authentication);
    }
}
