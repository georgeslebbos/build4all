package com.build4all.user.dto;

import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;

import java.time.LocalDateTime;

/**
 * UserDto = “safe + frontend-friendly” representation of Users entity.
 *
 * Key ideas:
 * 1) Don’t return the full Users entity to the client (it contains relations, lazy proxies, etc.).
 * 2) Flatten the most needed fields (name, contact, status, profile image).
 * 3) Include the tenant context via ownerProjectLinkId (AUP id) so the FE knows which app/tenant this user belongs to.
 * 4) Optionally expose adminId/projectId resolved from the link (convenience).
 */
public class UserDto {
    private Long id;

    // Basic profile info
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String phoneNumber;
    

    // UI fields
    private String profileImageUrl;
    private boolean publicProfile;

    // Account state
    private UserStatus status;
    private LocalDateTime lastLogin;

    /**
     * Tenant link ID (AdminUserProject. aup_id).
     * This is the best field for FE because it uniquely identifies the tenant/app context.
     */
    private Long ownerProjectLinkId;

    /**
     * Convenience fields resolved from ownerProjectLinkId.
     * Not strictly needed if FE can call link details by ownerProjectLinkId,
     * but helpful for debugging or quick usage.
     */
    private Long adminId;
    private Long projectId;

    public UserDto() {}

    /**
     * Convenience constructor: builds DTO from Users entity.
     *
     * It copies simple scalar fields directly, and then:
     * - checks ownerProject (AdminUserProject) relation
     * - extracts ownerProjectLinkId from ownerProject.getId() (aup_id)
     * - optionally extracts adminId and projectId through the relation graph
     *
     * NOTE: if Users.ownerProject is LAZY and you are outside a transaction,
     * accessing getOwnerProject() may trigger LazyInitializationException.
     * Typically you create this DTO inside service layer transaction or fetch join.
     */
    public UserDto(Users user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.username=user.getUsername();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhoneNumber();

        // Users stores profile picture as profilePictureUrl; DTO exposes it as profileImageUrl (naming for FE)
        this.profileImageUrl = user.getProfilePictureUrl();

        // Convert nullable Boolean -> primitive boolean safely
        this.publicProfile = user.getIsPublicProfile() != null && user.getIsPublicProfile();

        // Status + last login timestamps
        this.status = user.getStatus();
        this.lastLogin = user.getLastLogin();

        // Tenant context mapping
        if (user.getOwnerProject() != null) {
            // AdminUserProject primary key (aup_id)
            this.ownerProjectLinkId = user.getOwnerProject().getId();

            // Resolve adminId and projectId from the same link (if loaded)
            if (user.getOwnerProject().getAdmin() != null) {
                this.adminId = user.getOwnerProject().getAdmin().getAdminId();
            }
            if (user.getOwnerProject().getProject() != null) {
                this.projectId = user.getOwnerProject().getProject().getId();
            }
        }
    }

    // --------------------
    // Getters
    // --------------------
    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isPublicProfile() { return publicProfile; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public Long getOwnerProjectLinkId() { return ownerProjectLinkId; }
    public Long getAdminId() { return adminId; }
    public Long getProjectId() { return projectId; }

    // --------------------
    // Setters
    // --------------------
    public void setId(Long id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public void setStatus(UserStatus status) { this.status = status; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }
    public void setOwnerProjectLinkId(Long ownerProjectLinkId) { this.ownerProjectLinkId = ownerProjectLinkId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getUsername() { 
        return username; 
    }
    public void setUsername(String username) { 
        this.username = username; 
    }

}
