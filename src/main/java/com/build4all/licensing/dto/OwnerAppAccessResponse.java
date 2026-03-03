package com.build4all.licensing.dto;

import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.domain.SubscriptionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OwnerAppAccessResponse {

    private boolean canAccessDashboard;
    private String blockingReason;

    private PlanCode planCode;
    private String planName;

    private SubscriptionStatus subscriptionStatus;
    private LocalDate periodEnd;
    private long daysLeft;

    private Integer usersAllowed;
    private long activeUsers;
    private Long usersRemaining;

    private boolean requiresDedicatedServer;
    private boolean dedicatedInfraReady;

    // ✅ NEW: upgrade request state (latest request)
    private String upgradeRequestStatus;   // PENDING / APPROVED / REJECTED / null
    private PlanCode upgradeRequestedPlan; // requestedPlanCode
    private LocalDateTime upgradeRequestedAt;
    private String upgradeDecisionNote;

    public OwnerAppAccessResponse() {}

    public boolean isCanAccessDashboard() { return canAccessDashboard; }
    public void setCanAccessDashboard(boolean canAccessDashboard) { this.canAccessDashboard = canAccessDashboard; }

    public String getBlockingReason() { return blockingReason; }
    public void setBlockingReason(String blockingReason) { this.blockingReason = blockingReason; }

    public PlanCode getPlanCode() { return planCode; }
    public void setPlanCode(PlanCode planCode) { this.planCode = planCode; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(long daysLeft) { this.daysLeft = daysLeft; }

    public Integer getUsersAllowed() { return usersAllowed; }
    public void setUsersAllowed(Integer usersAllowed) { this.usersAllowed = usersAllowed; }

    public long getActiveUsers() { return activeUsers; }
    public void setActiveUsers(long activeUsers) { this.activeUsers = activeUsers; }

    public Long getUsersRemaining() { return usersRemaining; }
    public void setUsersRemaining(Long usersRemaining) { this.usersRemaining = usersRemaining; }

    public boolean isRequiresDedicatedServer() { return requiresDedicatedServer; }
    public void setRequiresDedicatedServer(boolean requiresDedicatedServer) { this.requiresDedicatedServer = requiresDedicatedServer; }

    public boolean isDedicatedInfraReady() { return dedicatedInfraReady; }
    public void setDedicatedInfraReady(boolean dedicatedInfraReady) { this.dedicatedInfraReady = dedicatedInfraReady; }

    // ✅ upgrade request getters/setters
    public String getUpgradeRequestStatus() { return upgradeRequestStatus; }
    public void setUpgradeRequestStatus(String upgradeRequestStatus) { this.upgradeRequestStatus = upgradeRequestStatus; }

    public PlanCode getUpgradeRequestedPlan() { return upgradeRequestedPlan; }
    public void setUpgradeRequestedPlan(PlanCode upgradeRequestedPlan) { this.upgradeRequestedPlan = upgradeRequestedPlan; }

    public LocalDateTime getUpgradeRequestedAt() { return upgradeRequestedAt; }
    public void setUpgradeRequestedAt(LocalDateTime upgradeRequestedAt) { this.upgradeRequestedAt = upgradeRequestedAt; }

    public String getUpgradeDecisionNote() { return upgradeDecisionNote; }
    public void setUpgradeDecisionNote(String upgradeDecisionNote) { this.upgradeDecisionNote = upgradeDecisionNote; }
}