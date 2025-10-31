package com.build4all.authentication.dto;

public class AdminPasswordUpdateDTO {

    private String username;          // Username of the admin
    private String currentPassword;   // Current password
    private String newPassword;       // New password to be set

    // Default constructor
    public AdminPasswordUpdateDTO() {}

    // Parameterized constructor
    public AdminPasswordUpdateDTO(String username, String currentPassword, String newPassword) {
        this.username = username;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    // Getters and setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
