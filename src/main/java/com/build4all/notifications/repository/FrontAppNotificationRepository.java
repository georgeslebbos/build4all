package com.build4all.notifications.repository;

import com.build4all.notifications.domain.FrontAppNotification;
import com.build4all.notifications.domain.NotificationActorType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FrontAppNotificationRepository extends JpaRepository<FrontAppNotification, Long> {

    List<FrontAppNotification> findByOwnerProjectLinkIdAndReceiverTypeAndReceiverIdOrderByCreatedAtDesc(
            Long ownerProjectLinkId,
            NotificationActorType receiverType,
            Long receiverId
    );

    int countByOwnerProjectLinkIdAndReceiverTypeAndReceiverIdAndIsReadFalse(
            Long ownerProjectLinkId,
            NotificationActorType receiverType,
            Long receiverId
    );

	Optional<FrontAppNotification> findByIdAndOwnerProjectLinkIdAndReceiverTypeAndReceiverId(Long notificationId, Long ownerProjectLinkId,
			NotificationActorType actorType, Long actorId);
}