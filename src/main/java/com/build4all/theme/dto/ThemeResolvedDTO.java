// src/main/java/com/build4all/theme/dto/ThemeResolvedDTO.java
package com.build4all.theme.dto;

import com.build4all.theme.domain.Theme;

/**
 * What the mobile app actually cares about.
 * You can extend later if you want (like exposing id/name too).
 */
public class ThemeResolvedDTO {

    private Long id;
    private String name;
    private String themeJson;

    public ThemeResolvedDTO() {
        this.id = null;
        this.name = "default";
        this.themeJson = "{}";
    }

    public ThemeResolvedDTO(Theme theme) {
        if (theme == null) {
            this.id = null;
            this.name = "default";
            this.themeJson = "{}";
        } else {
            this.id = theme.getId();
            this.name = theme.getName();
            this.themeJson = theme.getThemeJson() != null ? theme.getThemeJson() : "{}";
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getThemeJson() {
        return themeJson;
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
}
