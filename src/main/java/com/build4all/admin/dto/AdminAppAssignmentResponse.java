package com.build4all.admin.dto;

import java.time.LocalDate;

public record AdminAppAssignmentResponse(
    Long projectId,
    String projectName,
    String appName,
    String slug,
    String status,
    String licenseId,
    LocalDate validFrom,
    LocalDate endTo,
    Long themeId,
    String apkUrl
) {}
