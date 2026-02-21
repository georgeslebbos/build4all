package com.build4all.tutorial.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_tutorial")
public class PlatformTutorial {

    @Id
    @Column(length = 64, nullable = false)
    private String code;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}