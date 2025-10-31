package com.build4all.notifications.dto;

public class AdminNotificationPreferencesDTO {
    private String username; // ✅ Add this field
    private boolean notifyItemUpdates;
    private boolean notifyUserFeedback;

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isNotifyItemUpdates() {
        return notifyItemUpdates;
    }

    public void setNotifyItemUpdates(boolean notifyItemUpdates) {
        this.notifyItemUpdates = notifyItemUpdates;
    }

    public boolean isNotifyUserFeedback() {
        return notifyUserFeedback;
    }

    public void setNotifyUserFeedback(boolean notifyUserFeedback) {
        this.notifyUserFeedback = notifyUserFeedback;
    }
}
