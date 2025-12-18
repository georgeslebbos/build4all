package com.build4all.home.banner.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "home_banners")
public class HomeBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long id;

    // which generated app (AdminUserProject) this banner belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false)
    @JsonIgnore
    private AdminUserProject ownerProject;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "subtitle", length = 250)
    private String subtitle;

    /**
     * Deep link type:
     *   PRODUCT | CATEGORY | URL | NONE
     */
    @Column(name = "target_type", length = 30)
    private String targetType;

    // e.g. productId or categoryId if targetType != URL
    @Column(name = "target_id")
    private Long targetId;

    // if targetType == URL, open this URL
    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public HomeBanner() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --------- Getters & Setters ---------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
