package com.build4all.social.domain;

import com.build4all.user.domain.Users;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships")
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Users user; // Who sent the request

    @ManyToOne
    private Users friend; // Who received the request

    @Column(nullable = false)
    private String status; // "PENDING", "ACCEPTED", "REJECTED"

    @Column(name = "created_at")
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
