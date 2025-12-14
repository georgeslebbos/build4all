package com.build4all.business.domain;

import com.build4all.role.domain.Role;
import com.build4all.user.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(
        name = "business_user"
        // You will likely want tenant-safe uniqueness like:
        // UNIQUE (business_id, email), UNIQUE (business_id, username), UNIQUE (business_id, phone_number)
        // so the same email/username can exist in DIFFERENT businesses if you allow that.
)
public class BusinessUser implements UserDetails {

    /* =========================================================
     * Primary key
     * ========================================================= */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB auto-increment/identity column
    private Long id;

    /* =========================================================
     * Basic identity fields
     * ========================================================= */

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // Optional login identifiers (depending on your auth design)
    private String email;
    private String username;

    /**
     * Stored password hash (BCrypt recommended).
     * IMPORTANT: never store raw password here.
     * Also recommended: rename to passwordHash column explicitly with @Column(name="password_hash")
     * to match your other entities style.
     */
    private String passwordHash;

    private String phoneNumber;

    // Social identifiers (if you support Google sign-in)
    private String googleId;

    private String profilePictureUrl;

    /**
     * Public visibility flag for the business user profile.
     * Recommended: default to TRUE on create (like you did in Users/Businesses).
     */
    private Boolean isPublicProfile;

    /* =========================================================
     * Audit fields
     * ========================================================= */

    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /* =========================================================
     * Status + relations
     * ========================================================= */

    /**
     * Status reference (ACTIVE/INACTIVE/DELETED...).
     * You reused UserStatus table (works fine), but some teams prefer a separate BusinessUserStatus table.
     *
     * NOTE: Your join column name is "status" (FK column).
     * Equivalent SQL idea:
     *   business_user.status  -> user_status.id
     */
    @ManyToOne
    @JoinColumn(name = "status")
    private UserStatus status;

    /**
     * The owning Business (tenant boundary).
     * Many BusinessUsers belong to one Businesses row.
     *
     * fetch = LAZY:
     * - Good to avoid always loading the full business row.
     * - If you serialize BusinessUser, be careful with LazyInitialization unless you @JsonIgnore business.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false) // FK column in business_user table
    private Businesses business;

    /**
     * Authorization role for this BusinessUser (e.g. BUSINESS_OWNER, BUSINESS_MANAGER, BUSINESS_STAFF).
     *
     * @OnDelete(CASCADE):
     * - If a Role row is deleted, DB will delete business_user rows referencing it.
     * - Use with caution; often roles are "static" and should not be deleted in production.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsernameValue() { return username; } // helper to avoid confusion with UserDetails#getUsername()
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Businesses getBusiness() { return business; }
    public void setBusiness(Businesses business) { this.business = business; }

    public Boolean getPublicProfile() { return isPublicProfile; }
    public void setPublicProfile(Boolean publicProfile) { isPublicProfile = publicProfile; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    /* =========================================================
     * JPA lifecycle hooks (recommended)
     * ========================================================= */

    @PrePersist
    protected void onCreate() {
        // Initialize audit fields automatically when inserting row
        this.createdAt = this.updatedAt = LocalDateTime.now();

        // Default visibility to TRUE (avoid null surprises)
        if (this.isPublicProfile == null) this.isPublicProfile = true;
    }

    @PreUpdate
    protected void onUpdate() {
        // Update the "updatedAt" timestamp automatically on update
        this.updatedAt = LocalDateTime.now();
    }

    /* =========================================================
     * UserDetails implementation (Spring Security)
     * ========================================================= */

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Spring Security expects authorities like ROLE_OWNER, ROLE_MANAGER...
        if (role == null || role.getName() == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        // Used by Spring Security authentication providers (if you use DaoAuthenticationProvider)
        return passwordHash;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        // IMPORTANT:
        // - Be consistent with your authentication subject.
        // - If your JWT subject is email/phone, you may want to return email here instead.
        // - If you authenticate with username, keep this.
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        // If you implement expiration logic later, check it here.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        // Recommended: lock when status is INACTIVE or DELETED (like your Users entity).
        // Example:
        // if (status == null || status.getName() == null) return true;
        // String s = status.getName().toUpperCase();
        // return !s.equals("INACTIVE") && !s.equals("DELETED");
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        // If you rotate passwords / force re-login, check here.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        // Recommended: enabled only when ACTIVE (like your Users/Businesses).
        // Example:
        // return status != null && "ACTIVE".equalsIgnoreCase(status.getName());
        return true;
    }
}
