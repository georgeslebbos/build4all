package com.build4all.social.domain;

import com.build4all.user.domain.Users;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "friendships",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_friend_pair", columnNames = {"user_id","friend_id"})
    },
    indexes = {
        @Index(name = "idx_friendships_user", columnList = "user_id"),
        @Index(name = "idx_friendships_friend", columnList = "friend_id")
    }
)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_friendships_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE) // Hibernate hint (keep DB FK cascade too)
    private Users user;        // who sent the request

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "friend_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_friendships_friend")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users friend;      // who received the request

    @Column(nullable = false, length = 20)
    private String status;     // or use enum below

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Getters & setters
    public Long getId() { return id; }
    public Users getUser() { return user; }
    public Users getFriend() { return friend; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setUser(Users user) { this.user = user; }
    public void setFriend(Users friend) { this.friend = friend; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
