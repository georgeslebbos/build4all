package com.build4all.app.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Owner's request to create an app based on a project from the catalog.
 * Super Admin will approve or reject.
 */
@Entity
@Table(name = "app_request", indexes = {
        @Index(name = "idx_appreq_owner", columnList = "owner_id"),
        @Index(name = "idx_appreq_status", columnList = "status")
})
public class AppRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner/Requester ID (AdminUser in your current model) */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Target project from the catalog (projects.id) */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Display name for the app */
    @Column(name = "app_name", nullable = false, length = 128)
    private String appName;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING"; // PENDING / APPROVED / REJECTED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    /* Getters / Setters */
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
