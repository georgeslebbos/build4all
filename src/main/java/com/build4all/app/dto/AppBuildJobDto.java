package com.build4all.app.dto;

import java.time.LocalDateTime;

public record AppBuildJobDto(
        Long id,
        Long linkId,
        String platform,
        String status,
        String buildId,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String error,
        String apkUrl,
        String aabUrl,
        String ipaUrl
) {}
