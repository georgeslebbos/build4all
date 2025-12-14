// src/main/java/com/build4all/business/domain/BusinessStatus.java
package com.build4all.business.domain;

import jakarta.persistence.*;

/**
 * BusinessStatus
 * --------------
 * Lookup/reference table that defines the allowed statuses for a Business account.
 *
 * Typical rows:
 * - ACTIVE
 * - INACTIVE
 * - SUSPENDED
 * - DELETED (if you use soft-delete patterns)
 *
 * Why a separate table instead of an enum?
 * - You can manage statuses from DB (seed/migrations/admin screens).
 * - You can add new statuses without recompiling the backend.
 *
 * Used in Businesses.status (ManyToOne), and also by Spring Security checks:
 * - isEnabled() usually checks status == ACTIVE
 * - isAccountNonLocked() can block INACTIVE/DELETED, etc.
 */
@Entity
@Table(
        name = "business_status",
        uniqueConstraints = @UniqueConstraint(columnNames = "name") // Enforces unique status names in DB
        // Equivalent SQL:
        //   ALTER TABLE business_status ADD CONSTRAINT uk_business_status_name UNIQUE (name);
)
public class BusinessStatus {

    /**
     * Primary key.
     * IDENTITY means the database generates the value (auto-increment/serial).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Status name (unique).
     * Examples: ACTIVE, INACTIVE, SUSPENDED
     *
     * length=32 is a DB column length constraint (VARCHAR(32)).
     * unique=true duplicates the uniqueness rule (same effect as table unique constraint).
     */
    @Column(nullable = false, length = 32, unique = true)
    private String name; // e.g. ACTIVE, INACTIVE, SUSPENDED

    public BusinessStatus() {}

    public BusinessStatus(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }

    public void setId(Long id) { this.id = id; }

    /**
     * Prefer storing uppercase names (ACTIVE/INACTIVE/...) to simplify comparisons.
     * (You can enforce this at service layer or with DB constraints/triggers if needed.)
     */
    public void setName(String name) { this.name = name; }
}
