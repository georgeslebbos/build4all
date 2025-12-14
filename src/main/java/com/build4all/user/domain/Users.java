package com.build4all.user.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.order.domain.OrderItem;
import com.build4all.notifications.domain.Notifications;
import com.build4all.review.domain.Review;
import com.build4all.role.domain.Role;
import com.build4all.social.domain.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "users", // <- LOWERCASE physical table name (good for portability across DBs / naming strategies)
        uniqueConstraints = {
                // Ensures a user is unique *within the same tenant/app* (AUP = AdminUserProject)
                // This supports multi-tenant behavior: same email can exist in different apps/projects.
                @UniqueConstraint(name = "uk_user_app_email_link",  columnNames = {"aup_id", "email"}),
                @UniqueConstraint(name = "uk_user_app_phone_link",  columnNames = {"aup_id", "phone_number"}),
                @UniqueConstraint(name = "uk_user_app_user_link",   columnNames = {"aup_id", "username"})
        },
        indexes = {
                // Indexes help performance for common queries (login lookup, filtering by tenant, etc.)
                @Index(name = "idx_users_owner_project",  columnList = "aup_id"),
                @Index(name = "idx_users_email",          columnList = "email"),
                @Index(name = "idx_users_phone",          columnList = "phone_number"),
                @Index(name = "idx_users_username",       columnList = "username")
        }
)
public class Users implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** Tenant link (owner + project).
     *  Each user belongs to a specific "app instance" (AdminUserProject).
     *  This is the key that makes the Users table multi-tenant.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false)
    private AdminUserProject ownerProject;

    // Basic identity/profile fields used by UI and JWT claims.
    @Column(nullable = false) private String username;
    @Column(name = "first_name",  nullable = false) private String firstName;
    @Column(name = "last_name",   nullable = false) private String lastName;

    // Optional login identifiers (depends on your auth flow).
    @Column(name = "email")        private String email;
    @Column(name = "phone_number") private String phoneNumber;

    // Social login IDs (nullable; filled if the user registered via Google/Facebook).
    @Column(name = "google_id")   private String googleId;
    @Column(name = "facebook_id") private String facebookId;

    // Hashed password (BCrypt usually). Never store raw passwords.
    @Column(name = "password_hash", nullable = false) private String passwordHash;

    // Profile picture URL (local /uploads/** or external CDN).
    @Column(name = "profile_picture_url") private String profilePictureUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status")
    private UserStatus status; // nullable if you don’t always set it (but your isEnabled() expects it)

    /**
     * Role relation for Spring Security authorities (ROLE_USER, ROLE_ADMIN, ...).
     * You also used @OnDelete(CASCADE): if role is deleted, users linked to it will also be deleted.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    // Whether user's profile is visible in social features.
    @Column(name = "is_public_profile") private Boolean isPublicProfile = true;

    // Push notification token for Firebase Cloud Messaging.
    @Column(name = "fcm_token")  private String fcmToken;

    // Timestamp of last successful login.
    @Column(name = "last_login") private LocalDateTime lastLogin;

    // Audit timestamps (managed by @PrePersist / @PreUpdate below).
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;

    /* ---------- relations (unchanged) ---------- */
    // @JsonIgnore avoids infinite recursion when serializing entity relationships.

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<OrderItem> orderItems;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Friendship> sentFriendRequests;

    @JsonIgnore
    @OneToMany(mappedBy = "friend", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Friendship> receivedFriendRequests;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Posts> posts;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comments> comments;

    @JsonIgnore
    @OneToMany(mappedBy = "customer", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Review> reviews;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Notifications> notifications;

    @JsonIgnore
    @OneToMany(mappedBy = "sender", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<ChatMessages> sentMessages;

    @JsonIgnore
    @OneToMany(mappedBy = "receiver", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<ChatMessages> receivedMessages;

    /**
     * UserCategories appears to use an embedded id (id.user).
     * mappedBy = "id.user" means the owning side has an @EmbeddedId with a field "user".
     */
    @JsonIgnore
    @OneToMany(mappedBy = "id.user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<UserCategories> category;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PostLikes> postLikes = new ArrayList<>();

    /* ---------- lifecycle ---------- */
    // Automatically set timestamps when inserting/updating.
    @PrePersist protected void onCreate() { this.createdAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ========== UserDetails implementation ==========
    // These methods allow Spring Security to treat this entity as an authenticated principal.

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Converts Role entity -> Spring Security authority string.
        // Example: role=USER => authority=ROLE_USER
        if (role == null || role.getName() == null) {
            return Collections.emptyList();
        }
        String authority = "ROLE_" + role.getName().toUpperCase(); // USER -> ROLE_USER
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        // Spring Security expects "getPassword()" for authentication comparisons.
        return passwordHash;
    }

    // NOTE: You already have getUsername() as a normal getter below.
    // That method also satisfies UserDetails.getUsername().

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        // You are not tracking account expiration in DB, so always true.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        // Locks the account when status indicates INACTIVE/DELETED.
        // If status is null, we treat it as not locked to avoid blocking older data.
        if (status == null || status.getName() == null) return true;
        String s = status.getName().toUpperCase();
        return !s.equals("INACTIVE") && !s.equals("DELETED");
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        // You are not tracking credential expiration, so always true.
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        // Enabled only when status is ACTIVE.
        // If status is null, this returns false (user cannot authenticate).
        return status != null && "ACTIVE".equalsIgnoreCase(status.getName());
    }

    /* ---------- getters/setters ---------- */
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    // This getter also fulfills UserDetails.getUsername()
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

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getFacebookId() { return facebookId; }
    public void setFacebookId(String facebookId) { this.facebookId = facebookId; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Boolean getIsPublicProfile() { return isPublicProfile; }
    public void setIsPublicProfile(Boolean isPublicProfile) { this.isPublicProfile = isPublicProfile; }
    public boolean isPublicProfile() { return Boolean.TRUE.equals(this.isPublicProfile); }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // NOTE: Naming is a bit odd (getItemorders). Keeping it as-is.
    public List<OrderItem> getItemorders() { return orderItems; }
    public void setItemorders(List<OrderItem> orderItems) { this.orderItems = orderItems; }

    public List<Friendship> getSentFriendRequests() { return sentFriendRequests; }
    public void setSentFriendRequests(List<Friendship> sentFriendRequests) { this.sentFriendRequests = sentFriendRequests; }

    public List<Friendship> getReceivedFriendRequests() { return receivedFriendRequests; }
    public void setReceivedFriendRequests(List<Friendship> receivedFriendRequests) { this.receivedFriendRequests = receivedFriendRequests; }

    public List<Posts> getPosts() { return posts; }
    public void setPosts(List<Posts> posts) { this.posts = posts; }

    public List<Comments> getComments() { return comments; }
    public void setComments(List<Comments> comments) { this.comments = comments; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public List<Notifications> getNotifications() { return notifications; }
    public void setNotifications(List<Notifications> notifications) { this.notifications = notifications; }

    public List<ChatMessages> getSentMessages() { return sentMessages; }
    public void setSentMessages(List<ChatMessages> sentMessages) { this.sentMessages = sentMessages; }

    public List<ChatMessages> getReceivedMessages() { return receivedMessages; }
    public void setReceivedMessages(List<ChatMessages> receivedMessages) { this.receivedMessages = receivedMessages; }

    public List<UserCategories> getCategory() { return category; }
    public void setCategory(List<UserCategories> category) { this.category = category; }

    public List<PostLikes> getPostLikes() { return postLikes; }
    public void setPostLikes(List<PostLikes> postLikes) { this.postLikes = postLikes; }
}
