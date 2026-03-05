package com.build4all.licensing.dto;

import com.build4all.licensing.domain.PlanCode;

import java.time.LocalDateTime;

public class PendingUpgradeRequestRow {

    private final Long id;
    private final Long aupId;

    // ✅ app info
    private final String appName;
    private final String slug;

    // request info
    private final PlanCode requestedPlanCode;
    private final Integer usersAllowedOverride;
    private final LocalDateTime requestedAt;

    public PendingUpgradeRequestRow(
            Long id,
            Long aupId,
            String appName,
            String slug,
            PlanCode requestedPlanCode,
            Integer usersAllowedOverride,
            LocalDateTime requestedAt
    ) {
        this.id = id;
        this.aupId = aupId;
        this.appName = appName;
        this.slug = slug;
        this.requestedPlanCode = requestedPlanCode;
        this.usersAllowedOverride = usersAllowedOverride;
        this.requestedAt = requestedAt;
    }

    public Long getId() { return id; }
    public Long getAupId() { return aupId; }
    public String getAppName() { return appName; }
    public String getSlug() { return slug; }
    public PlanCode getRequestedPlanCode() { return requestedPlanCode; }
    public Integer getUsersAllowedOverride() { return usersAllowedOverride; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
}