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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// A saved AI answer, so the same question about the same resume is never sent to the AI twice.
@Entity
@Table(name = "ai_responses")
public class AiCachedResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Resume resume;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "job_description_hash", nullable = false, length = 64)
    private String jobDescriptionHash;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiCachedResponse() {
    }

    public AiCachedResponse(Resume resume, String action, String jobDescriptionHash, String responseJson) {
        this.resume = resume;
        this.action = action;
        this.jobDescriptionHash = jobDescriptionHash;
        this.responseJson = responseJson;
    }

    public Long getId() { return id; }
    public Resume getResume() { return resume; }
    public String getAction() { return action; }
    public String getJobDescriptionHash() { return jobDescriptionHash; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
