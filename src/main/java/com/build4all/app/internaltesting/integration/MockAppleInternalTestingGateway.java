package com.build4all.app.internaltesting.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "build4all.ios-internal.apple", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockAppleInternalTestingGateway implements AppleInternalTestingGateway {

    private final Set<String> existingTeamEmails;
    private final Set<String> acceptedInvitationEmails;

    public MockAppleInternalTestingGateway(
            @Value("${build4all.ios-internal.mock.existing-team-emails:}") String existingTeamEmailsRaw,
            @Value("${build4all.ios-internal.mock.accepted-invitation-emails:}") String acceptedInvitationEmailsRaw
    ) {
        this.existingTeamEmails = Arrays.stream(existingTeamEmailsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        this.acceptedInvitationEmails = Arrays.stream(acceptedInvitationEmailsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    public AppleInternalTestingGatewayResult process(AppleInternalTestingCommand command) {
        String email = normalize(command.appleEmail());

        if (existingTeamEmails.contains(email)) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED,
                    "MOCK-APPLE-USER-" + Math.abs(email.hashCode()),
                    null,
                    "Mock Apple gateway: existing team user found and added to internal testing"
            );
        }

        return new AppleInternalTestingGatewayResult(
                AppleInternalTestingGatewayOutcome.INVITATION_SENT,
                null,
                "MOCK-INV-" + command.ownerProjectLinkId() + "-" + System.currentTimeMillis(),
                "Mock Apple gateway: invitation sent, waiting for owner acceptance"
        );
    }

    @Override
    public AppleInternalTestingGatewayResult syncInvitation(
            AppleInternalTestingCommand command,
            String invitationId
    ) {
        String email = normalize(command.appleEmail());

        if (acceptedInvitationEmails.contains(email)) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INVITATION_ACCEPTED_AND_ADDED,
                    "MOCK-APPLE-USER-" + Math.abs(email.hashCode()),
                    invitationId,
                    "Mock Apple gateway: invitation accepted and user added to internal testing"
            );
        }

        return new AppleInternalTestingGatewayResult(
                AppleInternalTestingGatewayOutcome.STILL_WAITING,
                null,
                invitationId,
                "Mock Apple gateway: still waiting for invitation acceptance"
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}