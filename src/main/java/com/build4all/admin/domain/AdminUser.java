package com.build4all.admin.domain;

import com.build4all.role.domain.Role;
import com.build4all.business.domain.Businesses;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "AdminUser")
/**
 * AdminUser represents a back-office/admin account in the system.
 * It implements Spring Security's UserDetails so it can be used directly by authentication/authorization.
 *
 * Main responsibilities of this entity:
 * - Store admin identity data (username, email, names)
 * - Store password hash (never the raw password)
 * - Link to a Role (SUPER_ADMIN / OWNER / MANAGER ...)
 * - Link to a Business (optional association depending on your domain rules)
 * - Maintain links to AdminUserProject entries (tenant/app instances owned/managed by this admin)
 */
public class AdminUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    /**
     * The hashed password stored in DB.
     * @JsonProperty("password") maps incoming JSON field "password" to this property when deserializing.
     * (This is commonly used so clients can send { "password": "..." } and you map it to passwordHash.)
     *
     * NOTE: This entity should normally NOT be returned directly in API responses.
     * If returned, you typically want to make passwordHash write-only or ignored.
     */
    @JsonProperty("password")
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Role of the admin user (e.g., SUPER_ADMIN, OWNER, MANAGER).
     * EAGER fetch ensures role is available immediately (useful for Spring Security authorities).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    // Notification preferences for the admin user.
    @Column(name = "notify_item_updates")
    private Boolean notifyItemUpdates = true;

    @Column(name = "notify_user_feedback")
    private Boolean notifyUserFeedback = true;

    /**
     * Optional link to a business context (depends on your model).
     * OnDelete CASCADE means if business is deleted, linked admins are removed at DB level.
     */
    @ManyToOne
    @JoinColumn(name = "business_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Businesses business;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** NEW: one-to-many to association */
    /**
     * Links to the projects/apps this admin is associated with (AdminUserProject records).
     * - mappedBy="admin": AdminUserProject has a field named "admin" holding the owning side
     * - cascade=ALL: persist/update/remove AdminUserProject entries when AdminUser changes
     * - orphanRemoval=true: removing a link from this set deletes the row from DB
     * @JsonIgnore avoids recursive serialization (AdminUser -> links -> AdminUser -> ...)
     */
    @JsonIgnore
    @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AdminUserProject> projectLinks = new HashSet<>();


    public AdminUser() {}

    public AdminUser(String username, String firstName, String lastName, String email, String passwordHash, Role role) {
        this.username = username; this.firstName = firstName; this.lastName = lastName;
        this.email = email; this.passwordHash = passwordHash; this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        // Automatically set createdAt when inserting a new row.
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        // Automatically update updatedAt whenever the entity is updated.
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters …

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    // You have only a setter here; the "getter" used by Spring Security is getUsername() below.
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Boolean getNotifyItemUpdates() { return notifyItemUpdates; }
    public void setNotifyItemUpdates(Boolean notifyItemUpdates) { this.notifyItemUpdates = notifyItemUpdates; }

    public Boolean getNotifyUserFeedback() { return notifyUserFeedback; }
    public void setNotifyUserFeedback(Boolean notifyUserFeedback) { this.notifyUserFeedback = notifyUserFeedback; }

    public Businesses getBusiness() { return business; }
    public void setBusiness(Businesses business) { this.business = business; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Set<AdminUserProject> getProjectLinks() { return projectLinks; }
    public void setProjectLinks(Set<AdminUserProject> projectLinks) { this.projectLinks = projectLinks; }

    // --- UserDetails implementation ---

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Converts the Role entity into Spring Security authorities.
        // Spring's hasRole("X") checks for "ROLE_X" under the hood, so we build "ROLE_" + roleName.
        if (role == null || role.getName() == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        // Spring Security uses this for authentication (PasswordEncoder.matches()).
        return passwordHash;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        // you can choose username OR email, but be consistent with how you authenticate
        // This returns the identifier stored in username column (not email).
        return username;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        // Always true for now; can later be tied to an expiry field/status.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        // Always true for now; can later be tied to lock flag or failed login attempts.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        // Always true for now; can later support password expiration policies.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        // Always true for now; can later be tied to active/inactive status.
        return true;
    }

    /** convenience helpers */
    public void addProjectLink(AdminUserProject link) {
        // Maintains the bidirectional relationship consistency:
        // - add to collection
        // - set back-reference on the child entity
        projectLinks.add(link);
        link.setAdmin(this);
    }

    public void removeProjectLink(AdminUserProject link) {
        // Removing also nulls the back-reference; with orphanRemoval=true the link row is deleted from DB.
        projectLinks.remove(link);
        link.setAdmin(null);
    }
}
