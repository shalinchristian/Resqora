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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// One saved analysis per resume. Scores are plain columns; the findings list is stored as JSON text.
@Entity
@Table(name = "resume_analyses")
public class ResumeAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Resume resume;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "completeness_score", nullable = false)
    private int completenessScore;

    @Column(name = "skills_score", nullable = false)
    private int skillsScore;

    @Column(name = "projects_score", nullable = false)
    private int projectsScore;

    @Column(name = "experience_score", nullable = false)
    private int experienceScore;

    @Column(name = "education_score", nullable = false)
    private int educationScore;

    @Column(name = "findings_json", nullable = false, columnDefinition = "TEXT")
    private String findingsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResumeAnalysis() {
    }

    public ResumeAnalysis(Resume resume, int overallScore, int completenessScore, int skillsScore,
            int projectsScore, int experienceScore, int educationScore, String findingsJson) {
        this.resume = resume;
        this.overallScore = overallScore;
        this.completenessScore = completenessScore;
        this.skillsScore = skillsScore;
        this.projectsScore = projectsScore;
        this.experienceScore = experienceScore;
        this.educationScore = educationScore;
        this.findingsJson = findingsJson;
    }

    public Long getId() { return id; }
    public Resume getResume() { return resume; }
    public int getOverallScore() { return overallScore; }
    public int getCompletenessScore() { return completenessScore; }
    public int getSkillsScore() { return skillsScore; }
    public int getProjectsScore() { return projectsScore; }
    public int getExperienceScore() { return experienceScore; }
    public int getEducationScore() { return educationScore; }
    public String getFindingsJson() { return findingsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
