package com.build4all.admin.domain;

import com.build4all.role.domain.Role;
import com.build4all.business.domain.Businesses;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "AdminUser")
public class AdminUser {

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

    @JsonProperty("password")
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    @Column(name = "notify_item_updates")
    private Boolean notifyItemUpdates = true;

    @Column(name = "notify_user_feedback")
    private Boolean notifyUserFeedback = true;

    @ManyToOne
    @JoinColumn(name = "business_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Businesses business;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** NEW: one-to-many to association */
    @JsonIgnore
    @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AdminUserProject> projectLinks = new HashSet<>();
    

    public AdminUser() {}

    public AdminUser(String username, String firstName, String lastName, String email, String passwordHash, Role role) {
        this.username = username; this.firstName = firstName; this.lastName = lastName;
        this.email = email; this.passwordHash = passwordHash; this.role = role;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // getters/setters …

    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }

    public String getUsername() { return username; }
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

    /** convenience helpers */
    public void addProjectLink(AdminUserProject link) {
        projectLinks.add(link);
        link.setAdmin(this);
    }

    public void removeProjectLink(AdminUserProject link) {
        projectLinks.remove(link);
        link.setAdmin(null);
    }
}
