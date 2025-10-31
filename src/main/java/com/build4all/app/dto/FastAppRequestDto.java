// src/main/java/com/build4all/app/dto/FastAppRequestDto.java
package com.build4all.app.dto;

public record FastAppRequestDto(
    Long projectId,     // required
    Long themeId,       // optional (null => use active theme)
    String appName,     // required
    String slug,        // optional (auto from appName if null/blank)
    String appLogoUrl   // optional
) {}
