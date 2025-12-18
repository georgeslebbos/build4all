package com.build4all.home.dto;

import java.time.LocalDateTime;

public class HomeBannerResponse {

    private Long id;
    private String imageUrl;
    private String title;
    private String subtitle;
    private String targetType;
    private Long targetId;
    private String targetUrl;
    private int sortOrder;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    public HomeBannerResponse() {}

    public HomeBannerResponse(Long id, String imageUrl, String title, String subtitle,
                              String targetType, Long targetId, String targetUrl,
                              int sortOrder, LocalDateTime startAt, LocalDateTime endAt) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.title = title;
        this.subtitle = subtitle;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetUrl = targetUrl;
        this.sortOrder = sortOrder;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
}