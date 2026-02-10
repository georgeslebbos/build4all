package com.build4all.licensing.dto;

import com.build4all.licensing.domain.PlanCode;

public class UpgradePlanRequest {
    private PlanCode planCode;                 // FREE / PRO_HOSTEDB / DEDICATED ...
    private Integer usersAllowedOverride;      // optional (null = use plan default)

    public PlanCode getPlanCode() { return planCode; }
    public void setPlanCode(PlanCode planCode) { this.planCode = planCode; }

    public Integer getUsersAllowedOverride() { return usersAllowedOverride; }
    public void setUsersAllowedOverride(Integer usersAllowedOverride) { this.usersAllowedOverride = usersAllowedOverride; }
}
