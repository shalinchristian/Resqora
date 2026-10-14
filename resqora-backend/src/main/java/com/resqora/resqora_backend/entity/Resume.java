package com.resqora.resqora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "resumes")
public class Resume {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "document_type", nullable = false, length = 10)
    private String documentType;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
    private String extractedText;

    // original upload (PDF/DOCX), null for resumes uploaded before V8
    @Column(name = "file_data", length = Resume.MAX_FILE_BYTES)
    private byte[] fileData;

    @Column(name = "content_type", length = 100)
    private String contentType;

    // saved AI summary (made on first GET /summary)
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Resume() {
    }

    public Resume(User user, String originalFileName, String documentType, String extractedText) {
        this.user = user;
        this.originalFileName = originalFileName;
        this.documentType = documentType;
        this.extractedText = extractedText;
    }

    public Resume(User user, String originalFileName, String documentType, String extractedText,
                  byte[] fileData, String contentType) {
        this(user, originalFileName, documentType, extractedText);
        this.fileData = fileData;
        this.contentType = contentType;
    }

    public byte[] getFileData() { return fileData; }
    public String getContentType() { return contentType; }
    public boolean hasFile() { return fileData != null && contentType != null; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}