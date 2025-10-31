package com.build4all.user.repository;

import com.build4all.user.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserStatusRepository extends JpaRepository<UserStatus, Long> {
    Optional<UserStatus> findByName(String name);
    Optional<UserStatus> findByNameIgnoreCase(String name); 
    boolean existsByName(String name);
}
