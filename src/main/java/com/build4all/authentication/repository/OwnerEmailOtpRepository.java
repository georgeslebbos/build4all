package com.build4all.authentication.repository;

import com.build4all.authentication.domain.OwnerEmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OwnerEmailOtpRepository extends JpaRepository<OwnerEmailOtp, Long> {

    Optional<OwnerEmailOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByEmail(String email);

    // Renamed for clarity; boxed return to avoid primitive/null mismatch
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    Integer deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
