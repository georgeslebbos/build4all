package com.build4all.social.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.user.domain.Users;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Social post. Optionally scoped to an owner-project link (aup_id) for multi-tenant isolation.
 */
@Entity
@Table(name = "Posts")
public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url")
    private String imageUrl;

    @Column
    private String hashtags;

    @Column(name = "post_datetime", updatable = false)
    private LocalDateTime postDatetime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "visibility_id", nullable = false)
    private PostVisibility visibility;

    /** OPTIONAL: owner scoping (admin_user_projects.aup_id). Backward compatible if null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_link_id")
    @JsonIgnore
    private AdminUserProject ownerProject;

    /** Users who liked the post */
    @ManyToMany
    @JoinTable(
        name = "post_likes",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnore
    private Set<Users> likedUsers = new HashSet<>();

    /** Comments linked to this post */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Comments> comments = new ArrayList<>();

    public Posts() {}

    public Posts(Users user, String content, String imageUrl, String hashtags) {
        this.user = user;
        this.content = content;
        this.imageUrl = imageUrl;
        this.hashtags = hashtags;
    }

    @PrePersist
    protected void onCreate() {
        this.postDatetime = LocalDateTime.now();
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters & Setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getHashtags() { return hashtags; }
    public void setHashtags(String hashtags) { this.hashtags = hashtags; }

    public LocalDateTime getPostDatetime() { return postDatetime; }
    public void setPostDatetime(LocalDateTime postDatetime) { this.postDatetime = postDatetime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public PostVisibility getVisibility() { return visibility; }
    public void setVisibility(PostVisibility visibility) { this.visibility = visibility; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    public Set<Users> getLikedUsers() { return likedUsers; }
    public void setLikedUsers(Set<Users> likedUsers) { this.likedUsers = likedUsers; }

    public List<Comments> getComments() { return comments; }
    public void setComments(List<Comments> comments) { this.comments = comments; }

    // Convenience counters
    public int getLikeCount() { return likedUsers != null ? likedUsers.size() : 0; }
    public int getCommentCount() { return comments != null ? comments.size() : 0; }
}
