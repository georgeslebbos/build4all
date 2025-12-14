package com.build4all.business.repository;

import com.build4all.business.domain.BusinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * BusinessStatusRepository
 * ------------------------------------------------------------
 * Repository for the BusinessStatus lookup table.
 *
 * Typical rows:
 * - ACTIVE
 * - INACTIVE
 * - SUSPENDED
 *
 * This table is usually "global" (not tenant-scoped), because status values
 * are shared across all apps/tenants.
 */
@Repository
public interface BusinessStatusRepository extends JpaRepository<BusinessStatus, Long> {

    /**
     * Find a status row by name (case-insensitive).
     *
     * Example:
     * - findByNameIgnoreCase("active") -> returns row where name="ACTIVE"
     *
     * Equivalent SQL (conceptual):
     * SELECT *
     * FROM business_status bs
     * WHERE LOWER(bs.name) = LOWER(:name)
     * LIMIT 1;
     *
     * Note:
     * - LIMIT 1 is implied because we return Optional<BusinessStatus>.
     * - In many DBs, the optimizer may use an index on (name) if available.
     */
    Optional<BusinessStatus> findByNameIgnoreCase(String name);

    /**
     * Check if a status name exists (case-insensitive).
     *
     * Equivalent SQL (conceptual):
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM business_status bs
     * WHERE LOWER(bs.name) = LOWER(:name);
     *
     * Used for validations like:
     * - "Is this status already defined?"
     */
    boolean existsByNameIgnoreCase(String name);
}
