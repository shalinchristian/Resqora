package com.resqora.resqora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    // kept after the account is deleted, so it is not unique on its own (see activeEmail)
    @Column(nullable = false, length = 255)
    private String email;

    // same as email while the account is active, NULL once deleted: the UNIQUE constraint on this column
    // means one live account per email, and a deleted account's email can sign up again (V11)
    @Column(name = "active_email", unique = true, length = 255)
    private String activeEmail;

    // ACTIVE or DELETED (V11)
    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    // NULL for accounts older than V11 that never uploaded a resume
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // nullable: users created before names were added have no name
    @Column(length = 100)
    private String name;

    // false until the user types the code we emailed them (accounts from before V9 are true)
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // AI privacy setting: false = never send this user's data to an AI provider (V10, default true)
    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = true;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.activeEmail = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Soft delete: the row stays as a record (name, email, sign-up time) but can never log in again.
    // The password hash is replaced by a value no password can match.
    public void markDeleted(Instant when) {
        this.status = STATUS_DELETED;
        this.deletedAt = when;
        this.activeEmail = null;
        this.passwordHash = "DELETED";
    }

    public boolean isDeleted() {
        return STATUS_DELETED.equals(status);
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public User(String email, String passwordHash, String name) {
        this(email, passwordHash);
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }
}
