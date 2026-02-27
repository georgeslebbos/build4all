package com.build4all.security.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "auth_token_revocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_auth_token_revocations",
                columnNames = {"subject_type", "subject_id"}
        )
)
public class AuthTokenRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType; // USER / BUSINESS / ADMIN

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "revoked_after", nullable = false)
    private LocalDateTime revokedAfter;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- getters/setters ----
    public Long getId() { return id; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public LocalDateTime getRevokedAfter() { return revokedAfter; }
    public void setRevokedAfter(LocalDateTime revokedAfter) { this.revokedAfter = revokedAfter; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}