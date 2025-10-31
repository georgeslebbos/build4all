package com.build4all.business.domain;

import com.build4all.business.domain.Businesses;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "PendingManagers")
public class PendingManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pending_id")
    private Long id;

    @Column(nullable = false)
    private String email;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Businesses business;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public PendingManager() {}

    public PendingManager(String email, Businesses business, String token) {
        this.email = email;
        this.business = business;
        this.token = token;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Businesses getBusiness() {
        return business;
    }

    public void setBusiness(Businesses business) {
        this.business = business;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
