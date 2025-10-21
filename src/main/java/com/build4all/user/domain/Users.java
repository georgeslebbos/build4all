package com.build4all.user.domain;

import com.build4all.admin.domain.AdminUser;
import com.build4all.booking.domain.ItemBooking;
import com.build4all.notifications.domain.Notifications;
import com.build4all.project.domain.Project;
import com.build4all.review.domain.Review;
import com.build4all.social.domain.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "Users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_app_email",  columnNames = {"admin_id", "project_id", "email"}),
        @UniqueConstraint(name = "uk_user_app_phone",  columnNames = {"admin_id", "project_id", "phone_number"}),
        @UniqueConstraint(name = "uk_user_app_user",   columnNames = {"admin_id", "project_id", "username"})
    },
    indexes = {
        @Index(name = "idx_users_app",        columnList = "admin_id,project_id"),
        @Index(name = "idx_users_email",      columnList = "email"),
        @Index(name = "idx_users_phone",      columnList = "phone_number"),
        @Index(name = "idx_users_username",   columnList = "username")
    }
)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** which AdminUser (owner/admin) this app is under */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = true)
    private AdminUser owner;

    /** which Project this app is under */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /** uniqueness is per (admin_id, project_id) */
    @Column(name = "email")
    private String email;

    /** uniqueness is per (admin_id, project_id) */
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "facebook_id")
    private String facebookId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status")
    private UserStatus status;

    @Column(name = "is_public_profile")
    private Boolean isPublicProfile = true;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /* ---------- relations ---------- */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<ItemBooking> itemBookings;

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

    @JsonIgnore
    @OneToMany(mappedBy = "id.user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<UserCategories> category;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<PostLikes> postLikes = new ArrayList<>();

    /* ---------- lifecycle ---------- */
    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    /* ---------- getters / setters ---------- */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUser getOwner() { return owner; }
    public void setOwner(AdminUser owner) { this.owner = owner; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

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

    public List<ItemBooking> getItemBookings() { return itemBookings; }
    public void setItemBookings(List<ItemBooking> itemBookings) { this.itemBookings = itemBookings; }

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
