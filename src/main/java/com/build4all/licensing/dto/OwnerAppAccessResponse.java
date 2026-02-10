package com.build4all.licensing.dto;

import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.domain.SubscriptionStatus;

import java.time.LocalDate;

public class OwnerAppAccessResponse {

    private boolean canAccessDashboard;
    private String blockingReason; // null if allowed

    private PlanCode planCode;
    private String planName;

    private SubscriptionStatus subscriptionStatus;
    private LocalDate periodEnd;
    private long daysLeft;

    private Integer usersAllowed; // null = unlimited
    private long activeUsers;
    private Long usersRemaining;  // null = unlimited

    private boolean requiresDedicatedServer;
    private boolean dedicatedInfraReady;

    public OwnerAppAccessResponse() {}

    // getters/setters
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
}
