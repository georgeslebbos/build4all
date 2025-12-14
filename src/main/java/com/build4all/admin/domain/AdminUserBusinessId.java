package com.build4all.admin.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
/**
 * Composite key (Embeddable) used with @EmbeddedId in an entity that represents
 * a relationship between an Admin and a Business.
 *
 * This class contains the two IDs that together uniquely identify a row.
 */
public class AdminUserBusinessId implements Serializable {

    /**
     * Required for Serializable classes (used by JPA and caching/serialization mechanisms).
     */
    private static final long serialVersionUID = 1L;

    // Part 1 of the composite key: the business identifier.
    private Long businessId;

    // Part 2 of the composite key: the admin identifier.
    private Long adminId;

    // Default constructor (required by JPA)
    public AdminUserBusinessId() {}

    public AdminUserBusinessId(Long businessId, Long adminId) {
        // Initialize both parts of the composite key.
        this.businessId = businessId;
        this.adminId = adminId;
    }

    // Getters and Setters
    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        // Used by JPA when reading/writing the embedded id.
        this.businessId = businessId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        // Used by JPA when reading/writing the embedded id.
        this.adminId = adminId;
    }

    // equals() and hashCode() for correct comparison and storage in collections
    // JPA requires these to be implemented properly for composite keys.
    @Override
    public boolean equals(Object o) {
        // Two keys are equal if they represent the same (businessId, adminId).
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AdminUserBusinessId that = (AdminUserBusinessId) o;

        return Objects.equals(businessId, that.businessId) &&
                Objects.equals(adminId, that.adminId);
    }

    @Override
    public int hashCode() {
        // Must be consistent with equals() so the key works reliably in HashMap/HashSet.
        return Objects.hash(businessId, adminId);
    }
}
