package com.build4all.admin.dto;

import java.time.LocalDateTime;

public class AdminUserProfileDTO {
    private Long adminId;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String role;       // role name
    private Long businessId;   // nullable
    private Boolean notifyItemUpdates;
    private Boolean notifyUserFeedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AdminUserProfileDTO() {}

    public AdminUserProfileDTO(
            Long adminId,
            String username,
            String firstName,
            String lastName,
            String email,
            String role,
            Long businessId,
            Boolean notifyItemUpdates,
            Boolean notifyUserFeedback,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.adminId = adminId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.businessId = businessId;
        this.notifyItemUpdates = notifyItemUpdates;
        this.notifyUserFeedback = notifyUserFeedback;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

 
    // getters/setters
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }

    public Boolean getNotifyItemUpdates() { return notifyItemUpdates; }
    public void setNotifyItemUpdates(Boolean notifyItemUpdates) { this.notifyItemUpdates = notifyItemUpdates; }

    public Boolean getNotifyUserFeedback() { return notifyUserFeedback; }
    public void setNotifyUserFeedback(Boolean notifyUserFeedback) { this.notifyUserFeedback = notifyUserFeedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
