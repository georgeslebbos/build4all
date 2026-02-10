package com.build4all.licensing.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "plan_catalog")
public class PlanCatalog {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 30)
    private PlanCode code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "users_allowed")
    private Integer usersAllowed; // null = unlimited

    @Column(name = "requires_dedicated_server", nullable = false)
    private boolean requiresDedicatedServer = false;

    @Column(name = "billing_cycle_months", nullable = false)
    private int billingCycleMonths = 12;

    public PlanCatalog() {}

    public PlanCode getCode() { return code; }
    public void setCode(PlanCode code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getUsersAllowed() { return usersAllowed; }
    public void setUsersAllowed(Integer usersAllowed) { this.usersAllowed = usersAllowed; }

    public boolean isRequiresDedicatedServer() { return requiresDedicatedServer; }
    public void setRequiresDedicatedServer(boolean requiresDedicatedServer) { this.requiresDedicatedServer = requiresDedicatedServer; }

    public int getBillingCycleMonths() { return billingCycleMonths; }
    public void setBillingCycleMonths(int billingCycleMonths) { this.billingCycleMonths = billingCycleMonths; }
}
