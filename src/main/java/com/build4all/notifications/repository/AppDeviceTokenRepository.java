package com.build4all.notifications.repository;

import com.build4all.notifications.domain.AppDeviceToken;
import com.build4all.notifications.domain.AppScope;
import com.build4all.notifications.domain.NotificationActorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppDeviceTokenRepository extends JpaRepository<AppDeviceToken, Long> {

    Optional<AppDeviceToken> findByFcmToken(String fcmToken);

    List<AppDeviceToken> findByOwnerProjectLinkIdAndAppScopeAndActorTypeAndActorIdAndIsActiveTrue(
            Long ownerProjectLinkId,
            AppScope appScope,
            NotificationActorType actorType,
            Long actorId
    );

    List<AppDeviceToken> findByAppScopeAndActorTypeAndActorIdAndIsActiveTrue(
            AppScope appScope,
            NotificationActorType actorType,
            Long actorId
    );
}