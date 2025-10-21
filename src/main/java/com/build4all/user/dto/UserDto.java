package com.build4all.user.dto;

import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;

import java.time.LocalDateTime;

public class UserDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String profileImageUrl;
    private boolean publicProfile;

    private UserStatus status;
    private LocalDateTime lastLogin;

    // app info (optional but handy for FE)
    private Long adminId;
    private Long projectId;

    public UserDto() {}

    public UserDto(Users user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhoneNumber();
        this.profileImageUrl = user.getProfilePictureUrl();
        this.publicProfile = user.getIsPublicProfile() != null && user.getIsPublicProfile();
        this.status = user.getStatus();
        this.lastLogin = user.getLastLogin();
        this.adminId = user.getOwner() != null ? user.getOwner().getAdminId() : null;
        this.projectId = user.getProject() != null ? user.getProject().getId() : null;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isPublicProfile() { return publicProfile; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public Long getAdminId() { return adminId; }
    public Long getProjectId() { return projectId; }

    public void setId(Long id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public void setStatus(UserStatus status) { this.status = status; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}
