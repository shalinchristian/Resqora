package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.service.builder.ResumeDocxExporter;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/builder")
public class BuilderController {
    static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ResumeDocxExporter exporter;

    public BuilderController(ResumeDocxExporter exporter) {
        this.exporter = exporter;
    }

    // Builder form + template id -> DOCX download. Nothing is stored; the file holds only what was sent.
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody BuilderExportRequest request) {
        byte[] file = exporter.export(request);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(ResumeDocxExporter.fileName(request.name())).build().toString())
                .body(file);
    }
}
