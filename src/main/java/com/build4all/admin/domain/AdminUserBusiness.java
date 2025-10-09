package com.build4all.admin.domain;

import com.build4all.business.domain.Businesses;
import jakarta.persistence.*;


import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "AdminUserBusiness")
public class AdminUserBusiness
{

    @EmbeddedId
    private AdminUserBusinessId id;  // Composite key using @EmbeddedId

    @ManyToOne
    @MapsId("businessId")  // Maps the businessId field in the composite key
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "business_id", nullable = false)
    private Businesses business;

    @ManyToOne
    @MapsId("adminId")  // Maps the adminId field in the composite key
    @JoinColumn(name = "admin_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AdminUsers admin;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public AdminUserBusiness() {}

    public AdminUserBusiness(Businesses business, AdminUsers admin) {
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

    public AdminUsers getAdmin() {
        return admin;
    }

    public void setAdmin(AdminUsers admin) {
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
