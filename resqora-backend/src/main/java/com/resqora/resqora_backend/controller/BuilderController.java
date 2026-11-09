package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.service.builder.ResumeDocxExporter;
import com.resqora.resqora_backend.service.builder.ResumePdfExporter;
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

    private final ResumeDocxExporter docxExporter;
    private final ResumePdfExporter pdfExporter;

    public BuilderController(ResumeDocxExporter docxExporter, ResumePdfExporter pdfExporter) {
        this.docxExporter = docxExporter;
        this.pdfExporter = pdfExporter;
    }

    // Builder form + template id -> DOCX (default) or PDF ("format":"pdf") download.
    // Nothing is stored; the file holds only what was sent.
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody BuilderExportRequest request) {
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
}
