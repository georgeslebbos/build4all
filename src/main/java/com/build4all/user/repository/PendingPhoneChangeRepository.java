package com.build4all.user.repository;

import com.build4all.user.domain.PendingPhoneChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingPhoneChangeRepository extends JpaRepository<PendingPhoneChange, Long> {

    Optional<PendingPhoneChange> findByUser_IdAndOwnerProject_Id(Long userId, Long ownerProjectLinkId);

    void deleteByUser_IdAndOwnerProject_Id(Long userId, Long ownerProjectLinkId);
}