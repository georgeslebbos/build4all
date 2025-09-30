package com.build4all.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Users user;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Businesses business;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @ManyToOne
    @JoinColumn(name = "notification_type_id", nullable = false)
    private NotificationTypeEntity notificationType;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Notifications() {}

    public Notifications(Users user, String message, NotificationTypeEntity notificationType) {
        this.user = user;
        this.message = message;
        this.notificationType = notificationType;
    }

    public Notifications(Businesses business, String message, NotificationTypeEntity notificationType) {
        this.business = business;
        this.message = message;
        this.notificationType = notificationType;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public Businesses getBusiness() { return business; }
    public void setBusiness(Businesses business) { this.business = business; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationTypeEntity getNotificationType() { return notificationType; }
    public void setNotificationType(NotificationTypeEntity notificationType) { this.notificationType = notificationType; }

    public Boolean getRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
