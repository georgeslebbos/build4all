package com.build4all.admin.domain;

import com.build4all.business.domain.Businesses;
import jakarta.persistence.*;


import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "admin_user_businesses",
        uniqueConstraints = @UniqueConstraint(name="uk_aub_admin_business", columnNames={"admin_id","business_id"}),
        indexes = {
                @Index(name="idx_aub_admin", columnList="admin_id"),
                @Index(name="idx_aub_business", columnList="business_id")
        }
)
public class AdminUserBusiness {

    @EmbeddedId
    private AdminUserBusinessId id = new AdminUserBusinessId();

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @MapsId("businessId")
    @JoinColumn(name="business_id", nullable=false)
    private Businesses business;

    @ManyToOne(fetch = FetchType.LAZY, optional=false)
    @MapsId("adminId")
    @JoinColumn(name="admin_id", nullable=false)
    private AdminUser admin;

    @CreatedDate
    @Column(name="created_at", updatable=false, nullable=false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    // Constructors
    public AdminUserBusiness() {}

    public AdminUserBusiness(Businesses business, AdminUser admin) {
        this.business = business;
        this.admin = admin;
        this.id = new AdminUserBusinessId(business.getId(), admin.getAdminId());  // Initialize composite key
    }

    // Getters and Setters
    public AdminUserBusinessId getId() {
        return id;
    }

    public void setId(AdminUserBusinessId id) {
        this.id = id;
    }

    public Businesses getBusiness() {
        return business;
    }

    public void setBusiness(Businesses business) {
        this.business = business;
    }

    public AdminUser getAdmin() {
        return admin;
    }

    public void setAdmin(AdminUser admin) {
        this.admin = admin;
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
