// src/main/java/com/build4all/theme/dto/ThemeResponseDTO.java
package com.build4all.theme.dto;

import com.build4all.theme.domain.Theme;

public class ThemeResponseDTO {

    private Long id;
    private String name;
    private String themeJson;  // JSON string
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;

    public ThemeResponseDTO() {}

    public ThemeResponseDTO(Theme theme) {
        this.id = theme.getId();
        this.name = theme.getName();
        this.themeJson = theme.getThemeJson();
        this.isActive = theme.getIsActive() != null ? theme.getIsActive() : false;
        this.createdAt = theme.getCreatedAt() != null ? theme.getCreatedAt().toString() : null;
        this.updatedAt = theme.getUpdatedAt() != null ? theme.getUpdatedAt().toString() : null;
    }

    // Getters / setters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getThemeJson() {
        return themeJson;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setThemeJson(String themeJson) {
        this.themeJson = themeJson;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
