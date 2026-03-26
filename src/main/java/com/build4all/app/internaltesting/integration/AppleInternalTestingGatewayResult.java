package com.build4all.app.internaltesting.integration;

public record AppleInternalTestingGatewayResult(
        AppleInternalTestingGatewayOutcome outcome,
        String appleUserId,
        String invitationId,
        String appleBetaTesterId,
        String message
) {
}