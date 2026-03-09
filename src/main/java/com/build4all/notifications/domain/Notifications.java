package com.build4all.notifications.domain;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_user_read_created", columnList = "user_id, is_read, created_at"),
                @Index(name = "idx_notif_admin_read_created", columnList = "admin_id, is_read, created_at"),
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
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private AdminUser admin;

    /**
     * Transitional field kept temporarily so old business-based code still compiles.
     * We will stop using it in the next steps and remove it cleanly later.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonIgnore
    private Businesses business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_type_id", nullable = false)
    @JsonIgnore
    private NotificationTypeEntity notificationType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

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

    public Notifications(AdminUser admin, String message, NotificationTypeEntity notificationType) {
        this.admin = admin;
        this.message = message;
        this.notificationType = notificationType;
    }

    /**
     * Transitional constructor kept temporarily so old business-based code still compiles.
     */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public AdminUser getAdmin() {
        return admin;
    }

    public void setAdmin(AdminUser admin) {
        this.admin = admin;
    }

    public Businesses getBusiness() {
        return business;
    }

    public void setBusiness(Businesses business) {
        this.business = business;
    }

    public NotificationTypeEntity getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationTypeEntity notificationType) {
        this.notificationType = notificationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}