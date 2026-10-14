package com.resqora.resqora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// Last job suggestions for a resume (one row per resume), reused for 12 hours.
@Entity
@Table(name = "job_suggestions")
public class JobSuggestionCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Resume resume;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobSuggestionCache() {
    }

    public JobSuggestionCache(Resume resume, String responseJson) {
        this.resume = resume;
        refresh(responseJson);
    }

    // reuse the same row when the cache expires
    public void refresh(String responseJson) {
        this.responseJson = responseJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Resume getResume() { return resume; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
