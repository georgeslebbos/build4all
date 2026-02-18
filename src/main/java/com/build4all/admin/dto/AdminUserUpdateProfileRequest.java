package com.build4all.admin.dto;

public class AdminUserUpdateProfileRequest {

    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;

    private Boolean notifyItemUpdates;
    private Boolean notifyUserFeedback;

    private Boolean aiEnabled;

    // Optional password change
    private String currentPassword;
    private String newPassword;

    public AdminUserUpdateProfileRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Boolean getNotifyItemUpdates() { return notifyItemUpdates; }
    public void setNotifyItemUpdates(Boolean notifyItemUpdates) { this.notifyItemUpdates = notifyItemUpdates; }

    public Boolean getNotifyUserFeedback() { return notifyUserFeedback; }
    public void setNotifyUserFeedback(Boolean notifyUserFeedback) { this.notifyUserFeedback = notifyUserFeedback; }

    public Boolean getAiEnabled() { return aiEnabled; }
    public void setAiEnabled(Boolean aiEnabled) { this.aiEnabled = aiEnabled; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
