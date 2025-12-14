package com.build4all.business.repository;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * BusinessesRepository
 * ------------------------------------------------------------
 * Spring Data JPA repository for the Businesses entity.
 *
 * Key idea:
 * - "Legacy" methods: global lookups (NOT tenant-scoped). Kept to avoid breaking older code.
 * - "Tenant-aware" methods: always include ownerProjectLinkId (AdminUserProject.id) so queries are
 *   scoped to a specific app/tenant (aup_id in DB).
 *
 * Notes about SQL:
 * - Spring Data derives SQL from method names.
 * - Exact SQL varies slightly by DB and naming strategy, but the WHERE clauses below are equivalent.
 */
@Repository
public interface BusinessesRepository extends JpaRepository<Businesses, Long> {

    // ============================================================
    // Legacy (global) methods
    // WARNING: Not tenant-scoped. If the same email/phone/name exists in different apps,
    // these methods can return the wrong tenant's business.
    // ============================================================

    /**
     * Find a business by its business name (global).
     *
     * Equivalent SQL (conceptual):
     * SELECT *
     * FROM businesses b
     * WHERE b.business_name = :businessName
     * LIMIT 1;
     */
    Optional<Businesses> findByBusinessName(String businessName);

    /**
     * Check if a business name exists ignoring case (global).
     *
     * Equivalent SQL (conceptual):
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE LOWER(b.business_name) = LOWER(:businessName);
     */
    boolean existsByBusinessNameIgnoreCase(String businessName);

    /**
     * Check if a business name exists ignoring case, excluding a specific id (global).
     * Useful for "update" validation to avoid false conflict with the same record.
     *
     * Equivalent SQL (conceptual):
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE LOWER(b.business_name) = LOWER(:businessName)
     *   AND b.business_id <> :id;
     */
    boolean existsByBusinessNameIgnoreCaseAndIdNot(String businessName, Long id);

    /**
     * Find business by email (global).
     *
     * Equivalent SQL:
     * SELECT *
     * FROM businesses b
     * WHERE b.email = :email
     * LIMIT 1;
     */
    Optional<Businesses> findByEmail(String email);

    /**
     * Check if an email exists (global).
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.email = :email;
     */
    boolean existsByEmail(String email);

    /**
     * Find business by phone number (global).
     *
     * Equivalent SQL:
     * SELECT *
     * FROM businesses b
     * WHERE b.phone_number = :phoneNumber
     * LIMIT 1;
     */
    Optional<Businesses> findByPhoneNumber(String phoneNumber);

    /**
     * Check if a phone exists (global).
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.phone_number = :phoneNumber;
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find business by email (case-insensitive) OR by phone number (global).
     * Useful when user can log in via email or phone.
     *
     * Equivalent SQL (conceptual):
     * SELECT *
     * FROM businesses b
     * WHERE LOWER(b.email) = LOWER(:email)
     *    OR b.phone_number = :phoneNumber
     * LIMIT 1;
     */
    Optional<Businesses> findByEmailIgnoreCaseOrPhoneNumber(String email, String phoneNumber);

    /**
     * List all PUBLIC businesses with a given status (global).
     *
     * Equivalent SQL (conceptual):
     * SELECT *
     * FROM businesses b
     * WHERE b.is_public_profile = TRUE
     *   AND b.status = :statusId;
     *
     * NOTE: status is a ManyToOne -> BusinessStatus, so DB stores status as FK (status column).
     */
    List<Businesses> findByIsPublicProfileTrueAndStatus(BusinessStatus status);

    // ============================================================
    // Tenant-aware (scoped) methods
    // Uses ownerProjectLinkId (AdminUserProject.id), physically "aup_id" in businesses table.
    // ============================================================

    /**
     * Find business by tenant (ownerProjectLinkId) + email.
     *
     * Equivalent SQL (conceptual):
     * SELECT *
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND b.email = :email
     * LIMIT 1;
     */
    Optional<Businesses> findByOwnerProjectLink_IdAndEmail(Long ownerProjectLinkId, String email);

    /**
     * Find business by tenant (ownerProjectLinkId) + phone number.
     *
     * Equivalent SQL:
     * SELECT *
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND b.phone_number = :phone
     * LIMIT 1;
     */
    Optional<Businesses> findByOwnerProjectLink_IdAndPhoneNumber(Long ownerProjectLinkId, String phone);

    /**
     * Check if email exists inside a tenant.
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND b.email = :email;
     */
    boolean existsByOwnerProjectLink_IdAndEmail(Long ownerProjectLinkId, String email);

    /**
     * Check if phone exists inside a tenant.
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND b.phone_number = :phone;
     */
    boolean existsByOwnerProjectLink_IdAndPhoneNumber(Long ownerProjectLinkId, String phone);

    /**
     * Check if business name exists (ignore case) inside a tenant.
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND LOWER(b.business_name) = LOWER(:name);
     */
    boolean existsByOwnerProjectLink_IdAndBusinessNameIgnoreCase(Long ownerProjectLinkId, String name);

    /**
     * Check if business name exists (ignore case) inside a tenant,
     * excluding one business id (update validation).
     *
     * Equivalent SQL:
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND LOWER(b.business_name) = LOWER(:name)
     *   AND b.business_id <> :id;
     */
    boolean existsByOwnerProjectLink_IdAndBusinessNameIgnoreCaseAndIdNot(Long ownerProjectLinkId, String name, Long id);

    /**
     * List PUBLIC businesses with a given status inside a tenant.
     *
     * Equivalent SQL:
     * SELECT *
     * FROM businesses b
     * WHERE b.aup_id = :ownerProjectLinkId
     *   AND b.is_public_profile = TRUE
     *   AND b.status = :statusId;
     */
    List<Businesses> findByOwnerProjectLink_IdAndIsPublicProfileTrueAndStatus(Long ownerProjectLinkId, BusinessStatus status);
}
