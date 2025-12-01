// src/main/java/com/build4all/theme/domain/Theme.java
package com.build4all.theme.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_theme")
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Human-readable name for the theme (ex: "Dark Default", "Client A V1") */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Single JSON column that stores all theme values
     * (colors, typography, layout, etc.).
     */
    @Column(name = "theme_json", columnDefinition = "TEXT", nullable = false)
    private String themeJson = "{}";

    /** Whether this is the global default / active theme */
    @Column(name = "is_active")
    private Boolean isActive = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.themeJson == null || this.themeJson.isBlank()) {
            this.themeJson = "{}";
        }
        if (this.isActive == null) {
            this.isActive = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.themeJson == null || this.themeJson.isBlank()) {
            this.themeJson = "{}";
        }
        if (this.isActive == null) {
            this.isActive = false;
        }
    }

    // ===== Getters / Setters =====

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getThemeJson() {
        return themeJson;
    }

    public void setThemeJson(String themeJson) {
        this.themeJson = themeJson;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
