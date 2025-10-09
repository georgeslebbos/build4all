package com.build4all.notifications.repository;

import com.build4all.notifications.domain.NotificationTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTypeRepository extends JpaRepository<NotificationTypeEntity, Long> {
    Optional<NotificationTypeEntity> findByCode(String code);
}
