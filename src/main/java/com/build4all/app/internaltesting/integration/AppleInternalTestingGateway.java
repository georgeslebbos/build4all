package com.build4all.app.internaltesting.integration;

public interface AppleInternalTestingGateway {

    AppleInternalTestingGatewayResult process(AppleInternalTestingCommand command);

    AppleInternalTestingGatewayResult syncInvitation(
            AppleInternalTestingCommand command,
            String invitationId
    );
}