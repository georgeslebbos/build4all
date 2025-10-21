package com.build4all.user.repository;

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

    /* -------- app-scoped preferred finders -------- */

    Optional<Users> findByIdAndOwner_AdminIdAndProject_Id(Long id, Long adminId, Long projectId);

    Users findByEmailAndOwner_AdminIdAndProject_Id(String email, Long adminId, Long projectId);
    Users findByPhoneNumberAndOwner_AdminIdAndProject_Id(String phoneNumber, Long adminId, Long projectId);
    Users findByUsernameAndOwner_AdminIdAndProject_Id(String username, Long adminId, Long projectId);

    boolean existsByEmailAndOwner_AdminIdAndProject_Id(String email, Long adminId, Long projectId);
    boolean existsByPhoneNumberAndOwner_AdminIdAndProject_Id(String phone, Long adminId, Long projectId);
    boolean existsByUsernameIgnoreCaseAndOwner_AdminIdAndProject_Id(String username, Long adminId, Long projectId);

    Optional<Users> findByGoogleIdAndOwner_AdminIdAndProject_Id(String googleId, Long adminId, Long projectId);
    Users findByFacebookIdAndOwner_AdminIdAndProject_Id(String facebookId, Long adminId, Long projectId);

    /* -------- legacy/global (only if you still need them) -------- */
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
