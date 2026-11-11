package com.resqora.resqora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// One row per real AI call, used for the daily limits. Kept separate from the cache so that
// deleting a resume does not give the user their AI calls back.
@Entity
@Table(name = "ai_usage")
public class AiUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsage() {
    }

    public AiUsage(User user, Kind kind, Provider provider) {
        this.user = user;
        this.kind = kind;
        this.provider = provider;
    }

    // RESUME_CHAT / DASHBOARD_CHAT = one chat message, each counted in its own per-user daily limit
    // (a dashboard message answered by Gemini is still DASHBOARD_CHAT).
    // SUMMARY = a new resume summary, not in either chat limit but still in the provider's app-wide cap.
    public enum Kind { RESUME_CHAT, DASHBOARD_CHAT, SUMMARY }

    public enum Provider { GEMINI, GROQ }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Kind getKind() { return kind; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public Instant getCreatedAt() { return createdAt; }
}
