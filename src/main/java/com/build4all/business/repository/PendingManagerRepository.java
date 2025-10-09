package com.build4all.business.repository;

import com.build4all.business.domain.PendingManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PendingManagerRepository extends JpaRepository<PendingManager, Long> {

    Optional<PendingManager> findByToken(String token);

    Optional<PendingManager> findByEmail(String email);
}
