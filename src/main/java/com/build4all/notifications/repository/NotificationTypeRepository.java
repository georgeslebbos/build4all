package com.build4all.notifications.repository;

import com.build4all.notifications.domain.NotificationTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTypeRepository extends JpaRepository<NotificationTypeEntity, Long> {

    // Case-insensitive lookups on the canonical key
    Optional<NotificationTypeEntity> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);

    // If you want lookups by the human label (formerly "typeName"):
    Optional<NotificationTypeEntity> findByDescriptionIgnoreCase(String description);
    boolean existsByDescriptionIgnoreCase(String description);
}
