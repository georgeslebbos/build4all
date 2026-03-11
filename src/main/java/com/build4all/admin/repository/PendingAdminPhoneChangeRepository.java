package com.build4all.admin.repository;

import com.build4all.admin.domain.PendingAdminPhoneChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingAdminPhoneChangeRepository extends JpaRepository<PendingAdminPhoneChange, Long> {

    Optional<PendingAdminPhoneChange> findByAdmin_AdminId(Long adminId);

    void deleteByAdmin_AdminId(Long adminId);
}