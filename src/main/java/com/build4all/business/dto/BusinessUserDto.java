package com.build4all.business.dto;

public class BusinessUserDto {

    private Long id; // 👈 primary key

    private String firstName;
    private String lastName;
    private String email;

    // Optional fields (can be null)
    private String username;
    private String passwordHash;
    private String phoneNumber;
    private String googleId;
    private String profilePictureUrl;
    private Boolean isPublicProfile;
    private Long statusId;

    // --- Constructors ---

    public BusinessUserDto() {
    }

    public BusinessUserDto(Long id, String firstName, String lastName, String email, String username,
                           String passwordHash, String phoneNumber, String googleId,
                           String profilePictureUrl, Boolean isPublicProfile, Long statusId) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.googleId = googleId;
        this.profilePictureUrl = profilePictureUrl;
        this.isPublicProfile = isPublicProfile;
        this.statusId = statusId;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public Boolean getIsPublicProfile() {
        return isPublicProfile;
    }

    public void setIsPublicProfile(Boolean isPublicProfile) {
        this.isPublicProfile = isPublicProfile;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }
}
