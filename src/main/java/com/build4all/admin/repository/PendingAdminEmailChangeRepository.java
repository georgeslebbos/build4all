package com.build4all.admin.repository;

import com.build4all.admin.domain.PendingAdminEmailChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingAdminEmailChangeRepository extends JpaRepository<PendingAdminEmailChange, Long> {
    Optional<PendingAdminEmailChange> findByAdmin_AdminId(Long adminId);
    void deleteByAdmin_AdminId(Long adminId);
}