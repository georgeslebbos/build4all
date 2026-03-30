package com.build4all.notifications.repository;

import com.build4all.notifications.domain.AppFirebaseConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppFirebaseConfigRepository extends JpaRepository<AppFirebaseConfig, Long> {
    Optional<AppFirebaseConfig> findByOwnerProjectLinkIdAndIsActiveTrue(Long ownerProjectLinkId);
}