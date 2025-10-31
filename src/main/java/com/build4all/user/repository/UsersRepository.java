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
 * UsersRepository
 * - Tenant-scoped finders using entity and link-id overloads
 * - Fetch-join and native fallbacks to avoid lazy issues and column name mismatches
 * - Legacy/global finders kept for backward compatibility
 */
@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    /* -------- tenant-scoped (entity) -------- */
    Optional<Users> findByIdAndOwnerProject(Long id, AdminUserProject link);
    Users findByEmailAndOwnerProject(String email, AdminUserProject link);
    Users findByPhoneNumberAndOwnerProject(String phoneNumber, AdminUserProject link);
    Users findByUsernameAndOwnerProject(String username, AdminUserProject link);
    boolean existsByEmailAndOwnerProject(String email, AdminUserProject link);
    boolean existsByPhoneNumberAndOwnerProject(String phone, AdminUserProject link);
    boolean existsByUsernameIgnoreCaseAndOwnerProject(String username, AdminUserProject link);
    Optional<Users> findByGoogleIdAndOwnerProject(String googleId, AdminUserProject link);
    Users findByFacebookIdAndOwnerProject(String facebookId, AdminUserProject link);

    /* -------- tenant-scoped (by link id) -------- */
    Optional<Users> findByIdAndOwnerProject_Id(Long id, Long ownerProjectLinkId);
    Users findByEmailAndOwnerProject_Id(String email, Long ownerProjectLinkId);
    Users findByPhoneNumberAndOwnerProject_Id(String phoneNumber, Long ownerProjectLinkId);
    Users findByUsernameAndOwnerProject_Id(String username, Long ownerProjectLinkId);
    boolean existsByEmailAndOwnerProject_Id(String email, Long ownerProjectLinkId);
    boolean existsByPhoneNumberAndOwnerProject_Id(String phone, Long ownerProjectLinkId);
    boolean existsByUsernameIgnoreCaseAndOwnerProject_Id(String username, Long ownerProjectLinkId);
    Optional<Users> findByGoogleIdAndOwnerProject_Id(String googleId, Long ownerProjectLinkId);
    Users findByFacebookIdAndOwnerProject_Id(String facebookId, Long ownerProjectLinkId);

    /* -------- eager fetch to avoid LazyInitialization in DTOs -------- */
    @Query("""
        SELECT u FROM Users u
          JOIN FETCH u.ownerProject op
          LEFT JOIN FETCH op.admin
          LEFT JOIN FETCH op.project
        WHERE u.id = :id AND op.id = :ownerProjectLinkId
    """)
    Optional<Users> fetchByIdAndOwnerProjectId(@Param("id") Long id,
                                               @Param("ownerProjectLinkId") Long ownerProjectLinkId);

    /* -------- native fallback (physical column names) -------- */
    @Query(value = """
        SELECT *
        FROM users u
        WHERE u.user_id = :id
          AND u.aup_id  = :ownerProjectLinkId
    """, nativeQuery = true)
    Optional<Users> findByPkAndAupId(@Param("id") Long id,
                                     @Param("ownerProjectLinkId") Long ownerProjectLinkId);

    /* -------- legacy/global (optional) -------- */
    Users findByEmail(String email);
    Users findByPhoneNumber(String phoneNumber);
    Users findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<Users> findByGoogleId(String googleId);
    Users findByFacebookId(String facebookId);

    long countByCreatedAtAfter(LocalDateTime date);

    @Query(value = """
        SELECT TO_CHAR(u.created_at, 'YYYY-MM') AS month, COUNT(*)
        FROM users u
        WHERE u.created_at >= :startDate
        GROUP BY TO_CHAR(u.created_at, 'YYYY-MM')
        ORDER BY TO_CHAR(u.created_at, 'YYYY-MM')
    """, nativeQuery = true)
    List<Object[]> countMonthlyRegistrations(@Param("startDate") LocalDateTime startDate);

    @Query("""
       SELECT DISTINCT ui.id.user FROM UserCategories ui
       WHERE ui.category IN :categories
         AND ui.id.user.id <> :userId
    """)
    List<Users> findUsersWithMatchingCategories(@Param("userId") Long userId,
                                                @Param("categories") List<Category> categories);
}
