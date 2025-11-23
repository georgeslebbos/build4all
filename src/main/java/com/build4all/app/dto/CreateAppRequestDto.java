package com.build4all.app.dto;

public record CreateAppRequestDto(
    Long projectId,
    String appName,
    String slug,
    String logoUrl,
    Long themeId,
    String themeJson,
    String notes
) {}
