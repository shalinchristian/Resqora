package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.exception.TooManyBuilderRequestsException;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.service.builder.BuilderRateLimiter;
import com.resqora.resqora_backend.service.builder.ResumeDocxExporter;
import com.resqora.resqora_backend.service.builder.ResumePdfExporter;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/builder")
public class BuilderController {
    static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ResumeDocxExporter docxExporter;
    private final ResumePdfExporter pdfExporter;
    private final ResumeService resumeService;
    private final BuilderRateLimiter limiter;

    public BuilderController(ResumeDocxExporter docxExporter, ResumePdfExporter pdfExporter,
            ResumeService resumeService, BuilderRateLimiter limiter) {
        this.docxExporter = docxExporter;
        this.pdfExporter = pdfExporter;
        this.resumeService = resumeService;
        this.limiter = limiter;
    }

    // Builder form + template id -> DOCX (default) or PDF ("format":"pdf") download.
    // Nothing is stored; the file holds only what was sent.
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody BuilderExportRequest request,
            Authentication authentication) {
        limit(BuilderRateLimiter.Action.EXPORT, authentication, "downloads");
        boolean pdf = request.wantsPdf();
        byte[] file = pdf ? pdfExporter.export(request) : docxExporter.export(request);
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : DOCX)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ResumeDocxExporter.fileName(request.name(), pdf ? "pdf" : "docx"))
                        .build().toString())
                .body(file);
    }

    // Prefill the builder from a PDF/DOCX without saving it: same JSON as GET /api/resumes/{id}/parsed,
    // no resume slot used. Same 2 MB / PDF-or-DOCX / scanned-PDF rules as /api/resumes/upload.
    @PostMapping("/parse")
    public ParsedResume parse(@RequestParam("file") MultipartFile file, Authentication authentication) {
        limit(BuilderRateLimiter.Action.PARSE, authentication, "files read");
        return resumeService.parseWithoutSaving(file);
    }

    private void limit(BuilderRateLimiter.Action action, Authentication authentication, String what) {
        long wait = limiter.tryAcquire(action, (Long) authentication.getPrincipal());
        if (wait > 0) {
            throw new TooManyBuilderRequestsException(what, wait);
        }
    }
}
