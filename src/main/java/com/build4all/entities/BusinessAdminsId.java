package com.build4all.entities;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BusinessAdminsId implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Long businessId;
    private Long adminId;

    // Default constructor
    public BusinessAdminsId() {}

    
    public BusinessAdminsId(Long businessId, Long adminId) {
        this.businessId = businessId;
        this.adminId = adminId;
    }

    // Getters and Setters
    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    // equals() and hashCode() for correct comparison and storage in collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessAdminsId that = (BusinessAdminsId) o;
        return Objects.equals(businessId, that.businessId) &&
                Objects.equals(adminId, that.adminId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(businessId, adminId);
    }
}
