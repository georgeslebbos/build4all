package com.build4all.admin.dto;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) used to expose an AdminUser profile to the API/client.
 *
 * Why DTO?
 * - Avoid returning the full AdminUser entity (which may include relations and security-sensitive fields)
 * - Provide a clean, stable response contract for the frontend
 * - Flatten relations into simple fields (role name, businessId)
 */
public class AdminUserProfileDTO {

    // Primary identifier of the admin account (admin_id in DB).
    private Long adminId;

    // Login/display username.
    private String username;

    // Basic profile fields.
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;


    // Role name only (e.g., "SUPER_ADMIN", "OWNER", "MANAGER") instead of returning the full Role entity.
    private String role;

    // Business foreign key (nullable => admin may not be linked to a business).
    private Long businessId;

    // Notification preferences shown/edited in profile settings.
    private Boolean notifyItemUpdates;
    private Boolean notifyUserFeedback;

    // Audit timestamps copied from AdminUser entity.
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Default constructor needed for JSON deserialization frameworks.
    public AdminUserProfileDTO() {}

    public AdminUserProfileDTO(
            Long adminId,
            String username,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
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
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.businessId = businessId;
        this.notifyItemUpdates = notifyItemUpdates;
        this.notifyUserFeedback = notifyUserFeedback;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }


    // getters/setters
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) {
        // Used mainly by serialization frameworks or when building DTOs programmatically.
        this.adminId = adminId;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) {
        // Username shown in profile and potentially used for login (depending on your auth strategy).
        this.username = username;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) {
        // Admin first name displayed in UI.
        this.firstName = firstName;
    }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) {
        // Admin last name displayed in UI.
        this.lastName = lastName;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) {
        // Email displayed in UI and may be used for login/notifications.
        this.email = email;
    }

    public String getRole() { return role; }
    public void setRole(String role) {
        // Role name string (e.g., "SUPER_ADMIN") so frontend can display/decide UI permissions.
        this.role = role;
    }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }


    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) {
        // Nullable: only set if the admin is linked to a business.
        this.businessId = businessId;
    }

    public Boolean getNotifyItemUpdates() { return notifyItemUpdates; }
    public void setNotifyItemUpdates(Boolean notifyItemUpdates) {
        // Controls whether admin receives notifications about item updates.
        this.notifyItemUpdates = notifyItemUpdates;
    }

    public Boolean getNotifyUserFeedback() { return notifyUserFeedback; }
    public void setNotifyUserFeedback(Boolean notifyUserFeedback) {
        // Controls whether admin receives notifications about user feedback/reviews.
        this.notifyUserFeedback = notifyUserFeedback;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) {
        // Account creation timestamp.
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        // Last update timestamp.
        this.updatedAt = updatedAt;
    }
}
