package com.build4all.app.dto;

import java.time.LocalDate;

public record SuperAdminAppDetailsDto(
        Long linkId,
        Long ownerId,
        String ownerUsername,
        Long projectId,
        String projectName,
        String slug,
        String appName,
        String status,

        String licenseId,
        Long themeId,
        String logoUrl,

        LocalDate validFrom,
        LocalDate endTo,

        Long currencyId,

        String androidPackageName,
        String androidVersionName,
        Integer androidVersionCode,
        String apkUrl,
        String bundleUrl,

        String iosBundleId,
        String iosVersionName,
        Integer iosBuildNumber,
        String ipaUrl
) {}
