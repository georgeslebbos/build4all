package com.build4all.repositories;

import com.build4all.entities.Interest;
import com.build4all.entities.UserStatus;
import com.build4all.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findById(Long id);

    Users findByEmail(String email);

    Users findByUsername(String username);

    Users findByPhoneNumber(String phoneNumber); // ✅ Use the correct return type (not Object)

    long countByCreatedAtAfter(LocalDateTime date);

    @Query("""
        SELECT TO_CHAR(u.createdAt, 'YYYY-MM') AS month, COUNT(u)
        FROM Users u
        WHERE u.createdAt >= :startDate
        GROUP BY TO_CHAR(u.createdAt, 'YYYY-MM')
        ORDER BY TO_CHAR(u.createdAt, 'YYYY-MM')
    """)
    List<Object[]> countMonthlyRegistrations(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT DISTINCT ui.id.user FROM UserInterests ui " +
    	       "WHERE ui.interest IN :interests " +
    	       "AND ui.id.user.id <> :userId")
    	List<Users> findUsersWithMatchingInterests(@Param("userId") Long userId,
    	                                           @Param("interests") List<Interest> interests);

    Optional<Users> findByGoogleId(String googleId); // ✅ correct type



	  // add these if missing:
    Users findByFacebookId(String facebookId);
    boolean existsByUsernameIgnoreCase(String username);

}
