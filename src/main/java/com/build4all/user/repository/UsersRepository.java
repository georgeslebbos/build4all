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

    Users findByEmail(String email);

    Users findByUsername(String username);

    Users findByPhoneNumber(String phoneNumber); // ✅ Use the correct return type (not Object)

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

    @Query("SELECT DISTINCT ui.id.user FROM UserCategories ui " +
    	       "WHERE ui.category IN :categories " +
    	       "AND ui.id.user.id <> :userId")
    	List<Users> findUsersWithMatchingCategories(@Param("userId") Long userId,
    	                                           @Param("categories") List<Category> categories);

    Optional<Users> findByGoogleId(String googleId); // ✅ correct type



	  // add these if missing:
    Users findByFacebookId(String facebookId);
    boolean existsByUsernameIgnoreCase(String username);

}
