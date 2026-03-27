package com.build4all.app.internaltesting.dto;

import com.build4all.app.internaltesting.domain.AppleTesterIdentityStatus;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequestStatus;

import java.time.LocalDateTime;

public record IosInternalTestingRequestResponseDto(
        Long id,
        Long ownerProjectLinkId,
        Long ownerId,
        Long projectId,
        String appNameSnapshot,
        String bundleIdSnapshot,
        String appleEmail,
        String firstName,
        String lastName,
        IosInternalTestingRequestStatus status,
        String appleUserId,
        String appleInvitationId,
        String lastError,
        LocalDateTime requestedAt,
        LocalDateTime processedAt,
        LocalDateTime acceptedAt,
        LocalDateTime readyAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        Long appleTesterIdentityId,
        String appleBetaTesterId,
        AppleTesterIdentityStatus identityStatus
) {
}