package com.build4all.admin.repository;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AdminUser entity.
 * Provides common lookup methods used in:
 * - authentication (find by username/email)
 * - business-scoped admin management
 * - role-based stats (countByRole...)
 */
public interface AdminUsersRepository extends JpaRepository<AdminUser, Long> {

    /**
     * Find an admin by username.
     * Typically used for login flows when username is the identifier.
     */
    Optional<AdminUser> findByUsername(String username);

    /**
     * Find an admin by email.
     * Typically used for login flows or account recovery.
     */
    Optional<AdminUser> findByEmail(String email);

    /**
     * Find an admin by username OR email.
     * Useful when the login field can accept either value.
     */
    Optional<AdminUser> findByUsernameOrEmail(String username, String email);

    /**
     * Find admin(s) with a given email within a specific business.
     * Used when the same email may exist across different businesses.
     */
    List<AdminUser> findByEmailAndBusiness(String email, Businesses business);

    /**
     * Find all admin users sharing the same email (across businesses).
     */
    List<AdminUser> findAllByEmail(String email);

    /**
     * Fast existence check by email.
     * Used to validate duplicates or ensure specific accounts exist (e.g., SUPER_ADMIN seeding).
     */
    boolean existsByEmail(String superAdminEmail);

    /**
     * Find by adminId (same as JpaRepository.findById for this entity since adminId is the @Id field).
     * Kept for readability/consistency in service code.
     */
    Optional<AdminUser> findByAdminId(Long adminId);

    /**
     * Delete all admins linked to a given business id.
     * Usually used when a business is removed and all its admins should be removed too.
     */
    void deleteByBusiness_Id(Long businessId);

    /**
     * Count how many admins have a role with the given name (case-insensitive).
     * Useful for dashboards and for enforcing role-based limits.
     */
    int countByRole_NameIgnoreCase(String roleName);
    
    boolean existsByEmailIgnoreCaseAndAdminIdNot(String email, Long adminId);

    boolean existsByUsernameIgnoreCaseAndAdminIdNot(String username, Long adminId);

}
