package com.build4all.user.repository;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Category;
import com.build4all.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UsersRepository (Spring Data JPA)
 *
 * IMPORTANT IDEA:
 * - This project is multi-tenant using AdminUserProject as the "tenant link".
 * - The tenant link is stored in Users.ownerProject (ManyToOne) and physically in DB as users.aup_id.
 *
 * So you will see two styles of tenant-scoped methods:
 * 1) By passing the entity AdminUserProject link (ownerProject = :link)
 * 2) By passing the link id (ownerProject.id = :ownerProjectLinkId)
 *
 * And you also keep "legacy/global" methods (not tenant-safe) for backward compatibility.
 */
@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    /* =========================================================
     * A) TENANT-SCOPED FINDERS (pass AdminUserProject entity)
     * ========================================================= */

    /**
     * Find a user by primary key BUT only inside a specific tenant link.
     *
     * Equivalent SQL (logical):
     *   SELECT *
     *   FROM users
     *   WHERE user_id = :id
     *     AND aup_id  = :link.aup_id;
     */
    Optional<Users> findByIdAndOwnerProject(Long id, AdminUserProject link);

    /**
     * Find by email inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE email  = :email
     *     AND aup_id = :link.aup_id;
     */
    Users findByEmailAndOwnerProject(String email, AdminUserProject link);

    /**
     * Find by phone number inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE phone_number = :phoneNumber
     *     AND aup_id       = :link.aup_id;
     */
    Users findByPhoneNumberAndOwnerProject(String phoneNumber, AdminUserProject link);

    /**
     * Find by username inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE username = :username
     *     AND aup_id   = :link.aup_id;
     */
    Users findByUsernameAndOwnerProject(String username, AdminUserProject link);

    /**
     * Check existence by email inside a tenant link (fast EXISTS query).
     *
     * Equivalent SQL:
     *   SELECT EXISTS(
     *     SELECT 1 FROM users WHERE email=:email AND aup_id=:link.aup_id
     *   );
     */
    boolean existsByEmailAndOwnerProject(String email, AdminUserProject link);

    /**
     * Check existence by phone inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT EXISTS(
     *     SELECT 1 FROM users WHERE phone_number=:phone AND aup_id=:link.aup_id
     *   );
     */
    boolean existsByPhoneNumberAndOwnerProject(String phone, AdminUserProject link);

    /**
     * Check existence by username ignoring case inside a tenant link.
     * - Spring Data translates "IgnoreCase" to LOWER/UPPER comparisons depending on DB.
     *
     * Equivalent SQL (typical):
     *   SELECT EXISTS(
     *     SELECT 1
     *     FROM users
     *     WHERE LOWER(username)=LOWER(:username)
     *       AND aup_id=:link.aup_id
     *   );
     */
    boolean existsByUsernameIgnoreCaseAndOwnerProject(String username, AdminUserProject link);

    /**
     * Find by googleId inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE google_id = :googleId
     *     AND aup_id    = :link.aup_id;
     */
    Optional<Users> findByGoogleIdAndOwnerProject(String googleId, AdminUserProject link);

    /**
     * Find by facebookId inside a tenant link.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE facebook_id = :facebookId
     *     AND aup_id      = :link.aup_id;
     */
    Users findByFacebookIdAndOwnerProject(String facebookId, AdminUserProject link);


    /* =========================================================
     * B) TENANT-SCOPED FINDERS (pass link id: ownerProject.id)
     * ========================================================= */

    /**
     * Same as findByIdAndOwnerProject(...) but using the tenant link id directly.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE user_id = :id
     *     AND aup_id  = :ownerProjectLinkId;
     */
    Optional<Users> findByIdAndOwnerProject_Id(Long id, Long ownerProjectLinkId);

    /**
     * Find by email inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE email  = :email
     *     AND aup_id = :ownerProjectLinkId;
     */
    Users findByEmailAndOwnerProject_Id(String email, Long ownerProjectLinkId);

    /**
     * Find by phone inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE phone_number = :phoneNumber
     *     AND aup_id        = :ownerProjectLinkId;
     */
    Users findByPhoneNumberAndOwnerProject_Id(String phoneNumber, Long ownerProjectLinkId);

    /**
     * Find by username inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE username = :username
     *     AND aup_id   = :ownerProjectLinkId;
     */
    Users findByUsernameAndOwnerProject_Id(String username, Long ownerProjectLinkId);

    /**
     * Exists by email inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT EXISTS(
     *     SELECT 1 FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId
     *   );
     */
    boolean existsByEmailAndOwnerProject_Id(String email, Long ownerProjectLinkId);

    /**
     * Exists by phone inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT EXISTS(
     *     SELECT 1 FROM users WHERE phone_number=:phone AND aup_id=:ownerProjectLinkId
     *   );
     */
    boolean existsByPhoneNumberAndOwnerProject_Id(String phone, Long ownerProjectLinkId);

    /**
     * Exists by username ignoring case inside tenant using link id.
     *
     * Equivalent SQL (typical):
     *   SELECT EXISTS(
     *     SELECT 1
     *     FROM users
     *     WHERE LOWER(username)=LOWER(:username)
     *       AND aup_id=:ownerProjectLinkId
     *   );
     */
    boolean existsByUsernameIgnoreCaseAndOwnerProject_Id(String username, Long ownerProjectLinkId);

    /**
     * Find by googleId inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE google_id = :googleId
     *     AND aup_id    = :ownerProjectLinkId;
     */
    Optional<Users> findByGoogleIdAndOwnerProject_Id(String googleId, Long ownerProjectLinkId);

    /**
     * Find by facebookId inside tenant using link id.
     *
     * Equivalent SQL:
     *   SELECT *
     *   FROM users
     *   WHERE facebook_id = :facebookId
     *     AND aup_id      = :ownerProjectLinkId;
     */
    Users findByFacebookIdAndOwnerProject_Id(String facebookId, Long ownerProjectLinkId);


    /* =========================================================
     * C) FETCH-JOIN (eagerly load relations to avoid Lazy issues)
     * ========================================================= */

    /**
     * Fetch the user + ownerProject + (ownerProject.admin) + (ownerProject.project) in one query.
     *
     * Why:
     * - If you build DTOs that need admin/project info, LAZY relations can throw LazyInitializationException
     *   when accessed outside the transaction.
     *
     * Equivalent SQL (logical):
     *   SELECT u.*, op.*, a.*, p.*
     *   FROM users u
     *   JOIN admin_user_projects op ON op.aup_id = u.aup_id
     *   LEFT JOIN admin_user a      ON a.admin_id = op.admin_id
     *   LEFT JOIN project p         ON p.id = op.project_id
     *   WHERE u.user_id = :id AND op.aup_id = :ownerProjectLinkId;
     */
    @Query("""
        SELECT u FROM Users u
          JOIN FETCH u.ownerProject op
          LEFT JOIN FETCH op.admin
          LEFT JOIN FETCH op.project
        WHERE u.id = :id AND op.id = :ownerProjectLinkId
    """)
    Optional<Users> fetchByIdAndOwnerProjectId(@Param("id") Long id,
                                               @Param("ownerProjectLinkId") Long ownerProjectLinkId);


    /* =========================================================
     * D) NATIVE FALLBACK (exact physical column names)
     * ========================================================= */

    /**
     * Native query that bypasses JPQL parsing and uses physical DB columns.
     *
     * Why:
     * - Sometimes you get issues from naming strategies, column mismatches,
     *   or you want an exact SQL for debugging.
     *
     * Equivalent SQL: (this IS the SQL)
     *   SELECT *
     *   FROM users u
     *   WHERE u.user_id = :id
     *     AND u.aup_id  = :ownerProjectLinkId;
     */
    @Query(value = """
        SELECT *
        FROM users u
        WHERE u.user_id = :id
          AND u.aup_id  = :ownerProjectLinkId
    """, nativeQuery = true)
    Optional<Users> findByPkAndAupId(@Param("id") Long id,
                                     @Param("ownerProjectLinkId") Long ownerProjectLinkId);


    /* =========================================================
     * E) LEGACY / GLOBAL FINDERS (NOT tenant-safe)
     * ========================================================= */

    /**
     * Global lookup by email (ignores tenant).
     *
     * Equivalent SQL:
     *   SELECT * FROM users WHERE email=:email;
     *
     * WARNING:
     * - If you have multiple tenants, the same email may exist in multiple tenants.
     * - This returns "one row" (first match) depending on how Spring resolves it.
     */
    Users findByEmail(String email);

    /**
     * Global lookup by phone (ignores tenant).
     *
     * Equivalent SQL:
     *   SELECT * FROM users WHERE phone_number=:phoneNumber;
     */
    Users findByPhoneNumber(String phoneNumber);

    /**
     * Global lookup by username (ignores tenant).
     *
     * Equivalent SQL:
     *   SELECT * FROM users WHERE username=:username;
     */
    Users findByUsername(String username);

    /**
     * Global exists by username ignoring case (ignores tenant).
     *
     * Equivalent SQL (typical):
     *   SELECT EXISTS(
     *     SELECT 1 FROM users WHERE LOWER(username)=LOWER(:username)
     *   );
     */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Global lookup by googleId (ignores tenant).
     *
     * Equivalent SQL:
     *   SELECT * FROM users WHERE google_id=:googleId;
     */
    Optional<Users> findByGoogleId(String googleId);

    /**
     * Global lookup by facebookId (ignores tenant).
     *
     * Equivalent SQL:
     *   SELECT * FROM users WHERE facebook_id=:facebookId;
     */
    Users findByFacebookId(String facebookId);


    /* =========================================================
     * F) STATS HELPERS (for dashboards)
     * ========================================================= */

    /**
     * Count users created after a date.
     *
     * Equivalent SQL:
     *   SELECT COUNT(*) FROM users WHERE created_at > :date;
     */
    long countByCreatedAtAfter(LocalDateTime date);

    /**
     * Count registrations grouped by month from a start date (native query).
     *
     * Equivalent SQL: (this IS the SQL)
     *   SELECT TO_CHAR(created_at, 'YYYY-MM') AS month, COUNT(*)
     *   FROM users
     *   WHERE created_at >= :startDate
     *   GROUP BY TO_CHAR(created_at, 'YYYY-MM')
     *   ORDER BY TO_CHAR(created_at, 'YYYY-MM');
     */
    @Query(value = """
        SELECT TO_CHAR(u.created_at, 'YYYY-MM') AS month, COUNT(*)
        FROM users u
        WHERE u.created_at >= :startDate
        GROUP BY TO_CHAR(u.created_at, 'YYYY-MM')
        ORDER BY TO_CHAR(u.created_at, 'YYYY-MM')
    """, nativeQuery = true)
    List<Object[]> countMonthlyRegistrations(@Param("startDate") LocalDateTime startDate);


    /* =========================================================
     * G) MATCHING CATEGORIES (social / recommendations)
     * ========================================================= */

    /**
     * Find users who share categories with the given list, excluding the current user.
     *
     * JPQL meaning:
     * - ui is a row from UserCategories (join table)
     * - ui.id.user is the Users entity stored inside the composite key
     * - ui.category IN :categories means category_id IN (ids of provided Category objects)
     * - DISTINCT to avoid duplicates when multiple categories match the same user
     *
     * Equivalent SQL (logical):
     *   SELECT DISTINCT u.*
     *   FROM users u
     *   JOIN UserCategories uc ON uc.user_id = u.user_id
     *   WHERE uc.category_id IN (:categoryIds)
     *     AND u.user_id <> :userId;
     */
    @Query("""
       SELECT DISTINCT ui.id.user FROM UserCategories ui
       WHERE ui.category IN :categories
         AND ui.id.user.id <> :userId
    """)
    List<Users> findUsersWithMatchingCategories(@Param("userId") Long userId,
                                                @Param("categories") List<Category> categories);
}
