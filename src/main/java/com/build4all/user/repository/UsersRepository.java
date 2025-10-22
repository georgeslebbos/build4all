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

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    /* -------- tenant-scoped finders (via ownerProject link) -------- */

    Optional<Users> findByIdAndOwnerProject(Long id, AdminUserProject link);

    Users findByEmailAndOwnerProject(String email, AdminUserProject link);
    Users findByPhoneNumberAndOwnerProject(String phoneNumber, AdminUserProject link);
    Users findByUsernameAndOwnerProject(String username, AdminUserProject link);

    boolean existsByEmailAndOwnerProject(String email, AdminUserProject link);
    boolean existsByPhoneNumberAndOwnerProject(String phone, AdminUserProject link);
    boolean existsByUsernameIgnoreCaseAndOwnerProject(String username, AdminUserProject link);

    Optional<Users> findByGoogleIdAndOwnerProject(String googleId, AdminUserProject link);
    Users findByFacebookIdAndOwnerProject(String facebookId, AdminUserProject link);

    /* -------- legacy/global (keep only if needed) -------- */
    Users findByEmail(String email);
    Users findByPhoneNumber(String phoneNumber);
    Users findByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);

    long countByCreatedAtAfter(LocalDateTime date);

    @Query(
        value = """
            SELECT TO_CHAR(u.created_at, 'YYYY-MM') AS month, COUNT(*)
            FROM "Users" u
            WHERE u.created_at >= :startDate
            GROUP BY TO_CHAR(u.created_at, 'YYYY-MM')
            ORDER BY TO_CHAR(u.created_at, 'YYYY-MM')
        """,
        nativeQuery = true
    )
    List<Object[]> countMonthlyRegistrations(@Param("startDate") LocalDateTime startDate);

    @Query("""
       SELECT DISTINCT ui.id.user FROM UserCategories ui
       WHERE ui.category IN :categories
         AND ui.id.user.id <> :userId
    """)
    List<Users> findUsersWithMatchingCategories(@Param("userId") Long userId,
                                                @Param("categories") List<Category> categories);
}
