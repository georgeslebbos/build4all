package com.build4all.user.repository;

import com.build4all.user.domain.PendingEmailChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingEmailChangeRepository extends JpaRepository<PendingEmailChange, Long> {

    Optional<PendingEmailChange> findByUser_IdAndOwnerProject_Id(Long userId, Long ownerProjectLinkId);

    void deleteByUser_IdAndOwnerProject_Id(Long userId, Long ownerProjectLinkId);
}