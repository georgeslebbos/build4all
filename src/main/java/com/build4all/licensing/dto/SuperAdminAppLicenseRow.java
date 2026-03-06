package com.build4all.licensing.dto;

import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.domain.SubscriptionStatus;

import java.time.LocalDate;

public class SuperAdminAppLicenseRow {

    private Long aupId;

    private String appName;
    private String slug;
    private String appStatus;

    private Long adminId;
    private String ownerName;
    private String ownerEmail;
    private String ownerUsername;

    private Long projectId;
    private String projectName;

    private PlanCode planCode;
    private String planName;
    private SubscriptionStatus subscriptionStatus;
    private LocalDate periodEnd;
    private Long daysLeft;

    private Integer usersAllowed;
    private Long activeUsers;
    private Long usersRemaining;

    private Boolean requiresDedicatedServer;
    private Boolean dedicatedInfraReady;

    private Boolean canAccessDashboard;
    private String blockingReason;

    private String upgradeRequestStatus;

    public Long getAupId() {
        return aupId;
    }

    public void setAupId(Long aupId) {
        this.aupId = aupId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getAppStatus() {
        return appStatus;
    }

    public void setAppStatus(String appStatus) {
        this.appStatus = appStatus;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public PlanCode getPlanCode() {
        return planCode;
    }

    public void setPlanCode(PlanCode planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public Long getDaysLeft() {
        return daysLeft;
    }

    public void setDaysLeft(Long daysLeft) {
        this.daysLeft = daysLeft;
    }

    public Integer getUsersAllowed() {
        return usersAllowed;
    }

    public void setUsersAllowed(Integer usersAllowed) {
        this.usersAllowed = usersAllowed;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Long getUsersRemaining() {
        return usersRemaining;
    }

    public void setUsersRemaining(Long usersRemaining) {
        this.usersRemaining = usersRemaining;
    }

    public Boolean getRequiresDedicatedServer() {
        return requiresDedicatedServer;
    }

    public void setRequiresDedicatedServer(Boolean requiresDedicatedServer) {
        this.requiresDedicatedServer = requiresDedicatedServer;
    }

    public Boolean getDedicatedInfraReady() {
        return dedicatedInfraReady;
    }

    public void setDedicatedInfraReady(Boolean dedicatedInfraReady) {
        this.dedicatedInfraReady = dedicatedInfraReady;
    }

    public Boolean getCanAccessDashboard() {
        return canAccessDashboard;
    }

    public void setCanAccessDashboard(Boolean canAccessDashboard) {
        this.canAccessDashboard = canAccessDashboard;
    }

    public String getBlockingReason() {
        return blockingReason;
    }

    public void setBlockingReason(String blockingReason) {
        this.blockingReason = blockingReason;
    }

    public String getUpgradeRequestStatus() {
        return upgradeRequestStatus;
    }

    public void setUpgradeRequestStatus(String upgradeRequestStatus) {
        this.upgradeRequestStatus = upgradeRequestStatus;
    }
}