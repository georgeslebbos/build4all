package com.build4all.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class AdminUserUpdateProfileRequest {

    private String username;
    private String firstName;
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;

    private Boolean notifyItemUpdates;
    private Boolean notifyUserFeedback;

    private Boolean aiEnabled;

    // Optional password change
    private String currentPassword;

    @Size(min = 6, message = "newPassword must be at least 6 characters")
    private String newPassword;

    public AdminUserUpdateProfileRequest() {}

    // ---------- Normalizers (important) ----------
    private String normalizeNullable(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ✅ Conditional validation:
    // - if newPassword is provided -> currentPassword must be provided
    @AssertTrue(message = "Current password is required to change password")
    public boolean isPasswordChangeRequestValid() {
        String np = normalizeNullable(newPassword);
        String cp = normalizeNullable(currentPassword);

        // not changing password -> valid
        if (np == null) return true;

        // changing password -> must have current password
        return cp != null;
    }

    // ---------- getters/setters ----------
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = normalizeNullable(username); }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = normalizeNullable(firstName); }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = normalizeNullable(lastName); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normalizeNullable(email); }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) {
        // phone can be explicitly cleared by sending "" -> becomes null
        this.phoneNumber = normalizeNullable(phoneNumber);
    }

    public Boolean getNotifyItemUpdates() { return notifyItemUpdates; }
    public void setNotifyItemUpdates(Boolean notifyItemUpdates) { this.notifyItemUpdates = notifyItemUpdates; }

    public Boolean getNotifyUserFeedback() { return notifyUserFeedback; }
    public void setNotifyUserFeedback(Boolean notifyUserFeedback) { this.notifyUserFeedback = notifyUserFeedback; }

    public Boolean getAiEnabled() { return aiEnabled; }
    public void setAiEnabled(Boolean aiEnabled) { this.aiEnabled = aiEnabled; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = normalizeNullable(currentPassword);
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) {
        this.newPassword = normalizeNullable(newPassword);
    }
}