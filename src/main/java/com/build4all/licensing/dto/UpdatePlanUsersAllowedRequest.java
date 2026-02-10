package com.build4all.licensing.dto;

public class UpdatePlanUsersAllowedRequest {

    // null = unlimited
    private Integer usersAllowed;

    public Integer getUsersAllowed() { return usersAllowed; }
    public void setUsersAllowed(Integer usersAllowed) { this.usersAllowed = usersAllowed; }
}
