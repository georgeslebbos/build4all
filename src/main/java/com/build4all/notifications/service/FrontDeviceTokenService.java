package com.build4all.notifications.service;

import com.build4all.notifications.domain.AppDeviceToken;
import com.build4all.notifications.domain.AppScope;
import com.build4all.notifications.domain.NotificationActorType;
import com.build4all.notifications.dto.FrontDeviceTokenRequest;
import com.build4all.notifications.repository.AppDeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FrontDeviceTokenService {

    private final AppDeviceTokenRepository appDeviceTokenRepository;

    public FrontDeviceTokenService(AppDeviceTokenRepository appDeviceTokenRepository) {
        this.appDeviceTokenRepository = appDeviceTokenRepository;
    }

    @Transactional
    public AppDeviceToken upsertFrontToken(
            Long actorId,
            NotificationActorType actorType,
            Long ownerProjectLinkId,
            FrontDeviceTokenRequest request
    ) {
        if (actorId == null) {
            throw new RuntimeException("Actor id is required");
        }

        if (actorType == null) {
            throw new RuntimeException("Actor type is required");
        }

        if (ownerProjectLinkId == null) {
            throw new RuntimeException("ownerProjectLinkId is required");
        }

        if (request == null) {
            throw new RuntimeException("Request body is required");
        }

        if (!StringUtils.hasText(request.getFcmToken())) {
            throw new RuntimeException("fcmToken is required");
        }

        if (request.getPlatform() == null) {
            throw new RuntimeException("platform is required");
        }

        AppDeviceToken token = appDeviceTokenRepository
                .findByFcmToken(request.getFcmToken())
                .orElseGet(AppDeviceToken::new);

        token.setActorId(actorId);
        token.setActorType(actorType);
        token.setAppScope(AppScope.FRONT);
        token.setOwnerProjectLinkId(ownerProjectLinkId);
        token.setPlatform(request.getPlatform());
        token.setPackageName(trimToNull(request.getPackageName()));
        token.setBundleId(trimToNull(request.getBundleId()));
        token.setDeviceId(trimToNull(request.getDeviceId()));
        token.setFcmToken(request.getFcmToken().trim());
        token.setActive(true);

        return appDeviceTokenRepository.save(token);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}