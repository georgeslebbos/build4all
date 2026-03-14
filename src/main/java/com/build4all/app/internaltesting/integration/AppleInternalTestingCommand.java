package com.build4all.app.internaltesting.integration;

public record AppleInternalTestingCommand(
        Long requestId,
        Long ownerProjectLinkId,
        Long ownerId,
        Long projectId,
        String appName,
        String bundleId,
        String appleEmail,
        String firstName,
        String lastName
) {
}