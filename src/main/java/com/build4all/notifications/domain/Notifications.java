package com.build4all.notifications.domain;

import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_user_read_created", columnList = "user_id, is_read, created_at"),
                @Index(name = "idx_notif_business_read_created", columnList = "business_id, is_read, created_at")
        }
)
public class Notifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @org.hibernate.annotations.OnDelete(action = OnDeleteAction.CASCADE)
    @com.fasterxml.jackson.annotation.JsonIgnore         // ⬅️ ADD THIS
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    @org.hibernate.annotations.OnDelete(action = OnDeleteAction.CASCADE)
    @com.fasterxml.jackson.annotation.JsonIgnore         // ⬅️ ADD THIS
    private Businesses business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_type_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore         // ⬅️ ADD THIS
    private NotificationTypeEntity notificationType;


    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

   

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false; // use primitive to avoid null tri-state

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters / setters
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

    public boolean getIsRead() { return isRead; }             // ✅ conventional boolean getter
    public void setIsRead(boolean read) { isRead = read; }   // ✅ conventional setter

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
