package com.build4all.app.internaltesting.service;

import com.build4all.app.internaltesting.domain.AppleTesterIdentity;
import com.build4all.app.internaltesting.domain.AppleTesterIdentityStatus;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayOutcome;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayResult;
import com.build4all.app.internaltesting.repository.AppleTesterIdentityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AppleTesterIdentityService {

    private final AppleTesterIdentityRepository appleTesterIdentityRepository;

    public AppleTesterIdentityService(AppleTesterIdentityRepository appleTesterIdentityRepository) {
        this.appleTesterIdentityRepository = appleTesterIdentityRepository;
    }

    public AppleTesterIdentity findOrCreateIdentity(String appleEmail, String firstName, String lastName) {
        String normalizedEmail = normalizeEmail(appleEmail);

        Optional<AppleTesterIdentity> existing = appleTesterIdentityRepository.findByNormalizedEmail(normalizedEmail);
        if (existing.isPresent()) {
            AppleTesterIdentity identity = existing.get();

            identity.setOriginalEmail(appleEmail.trim());

            if (firstName != null && !firstName.isBlank()) {
                identity.setFirstName(firstName.trim());
            }

            if (lastName != null && !lastName.isBlank()) {
                identity.setLastName(lastName.trim());
            }

            return appleTesterIdentityRepository.save(identity);
        }
        AppleTesterIdentity identity = new AppleTesterIdentity();
        identity.setNormalizedEmail(normalizedEmail);
        identity.setOriginalEmail(appleEmail.trim());
        identity.setFirstName(requireName(firstName, "firstName"));
        identity.setLastName(requireName(lastName, "lastName"));
        identity.setStatus(AppleTesterIdentityStatus.NEW);
        identity.setSyncAttempts(0);

        return appleTesterIdentityRepository.save(identity);
    }

    @Transactional(readOnly = true)
    public Optional<AppleTesterIdentity> findById(Long identityId) {
        if (identityId == null) {
            return Optional.empty();
        }
        return appleTesterIdentityRepository.findById(identityId);
    }

    @Transactional(readOnly = true)
    public Optional<AppleTesterIdentity> findByEmail(String appleEmail) {
        if (appleEmail == null || appleEmail.isBlank()) {
            return Optional.empty();
        }
        return appleTesterIdentityRepository.findByNormalizedEmail(normalizeEmail(appleEmail));
    }

    public AppleTesterIdentity markInvitationSent(AppleTesterIdentity identity, String invitationId) {
        requireIdentity(identity);

        if (invitationId != null && !invitationId.isBlank()) {
            identity.markInvitationSent(invitationId.trim());
        } else {
            identity.setStatus(AppleTesterIdentityStatus.INVITATION_SENT);
            identity.setInvitedAt(LocalDateTime.now());
            identity.setLastError(null);
        }

        identity.setLastSyncedAt(LocalDateTime.now());
        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity markWaitingAcceptance(AppleTesterIdentity identity) {
        requireIdentity(identity);

        identity.markWaitingAcceptance();
        identity.setLastSyncedAt(LocalDateTime.now());

        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity markUserVisible(AppleTesterIdentity identity, String appleUserId) {
        requireIdentity(identity);

        if (appleUserId == null || appleUserId.isBlank()) {
            throw new IllegalArgumentException("appleUserId is required");
        }

        identity.markUserVisible(appleUserId.trim());
        identity.setLastSyncedAt(LocalDateTime.now());

        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity markBetaTesterReady(AppleTesterIdentity identity, String appleBetaTesterId) {
        requireIdentity(identity);

        if (appleBetaTesterId == null || appleBetaTesterId.isBlank()) {
            throw new IllegalArgumentException("appleBetaTesterId is required");
        }

        identity.markBetaTesterReady(appleBetaTesterId.trim());
        identity.setLastSyncedAt(LocalDateTime.now());

        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity markFailed(AppleTesterIdentity identity, String error) {
        requireIdentity(identity);

        identity.markFailed(
                error != null && !error.isBlank()
                        ? error.trim()
                        : "Unknown Apple identity error"
        );
        identity.setLastSyncedAt(LocalDateTime.now());

        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity incrementSyncAttempts(AppleTesterIdentity identity) {
        requireIdentity(identity);

        identity.incrementSyncAttempts();
        return appleTesterIdentityRepository.save(identity);
    }

    public AppleTesterIdentity applyGatewayResult(
            AppleTesterIdentity identity,
            AppleInternalTestingGatewayResult result
    ) {
        requireIdentity(identity);

        if (result == null || result.outcome() == null) {
            identity.markFailed("Apple gateway returned empty result");
            identity.setLastSyncedAt(LocalDateTime.now());
            return appleTesterIdentityRepository.save(identity);
        }

        identity.incrementSyncAttempts();

        if (result.invitationId() != null && !result.invitationId().isBlank()) {
            identity.setAppleInvitationId(result.invitationId().trim());
        }

        if (result.appleUserId() != null && !result.appleUserId().isBlank()) {
            identity.setAppleUserId(result.appleUserId().trim());
        }

        if (result.appleBetaTesterId() != null && !result.appleBetaTesterId().isBlank()) {
            identity.setAppleBetaTesterId(result.appleBetaTesterId().trim());
        }

        AppleInternalTestingGatewayOutcome outcome = result.outcome();

        switch (outcome) {
            case INVITATION_SENT -> {
                if (result.invitationId() != null && !result.invitationId().isBlank()) {
                    identity.markInvitationSent(result.invitationId().trim());
                } else {
                    identity.setStatus(AppleTesterIdentityStatus.INVITATION_SENT);
                    identity.setInvitedAt(LocalDateTime.now());
                    identity.setLastError(null);
                }
            }

            case STILL_WAITING, WAITING_FOR_APPLE_USER_SYNC -> {
                if (identity.getAppleBetaTesterId() != null && !identity.getAppleBetaTesterId().isBlank()) {
                    identity.setStatus(AppleTesterIdentityStatus.BETA_TESTER_READY);
                    identity.setLastError(null);
                } else if (identity.getAppleUserId() != null && !identity.getAppleUserId().isBlank()) {
                    identity.setStatus(AppleTesterIdentityStatus.USER_VISIBLE);
                    identity.setLastError(null);
                } else {
                    identity.setStatus(AppleTesterIdentityStatus.WAITING_ACCEPTANCE);
                    identity.setLastError(null);
                }
            }

            case INTERNAL_ACCESS_PENDING -> {
                if (result.appleBetaTesterId() != null && !result.appleBetaTesterId().isBlank()) {
                    identity.markBetaTesterReady(result.appleBetaTesterId().trim());
                } else if (result.appleUserId() != null && !result.appleUserId().isBlank()) {
                    identity.markUserVisible(result.appleUserId().trim());
                } else if (result.invitationId() != null && !result.invitationId().isBlank()) {
                    identity.markWaitingAcceptance();
                }
            }

            case INVITATION_ACCEPTED_AND_ADDED, EXISTING_USER_ADDED -> {
                if (result.appleBetaTesterId() != null && !result.appleBetaTesterId().isBlank()) {
                    identity.markBetaTesterReady(result.appleBetaTesterId().trim());
                } else if (result.appleUserId() != null && !result.appleUserId().isBlank()) {
                    identity.markUserVisible(result.appleUserId().trim());
                } else {
                    identity.setLastError(null);
                }
            }
        }

        identity.setLastSyncedAt(LocalDateTime.now());
        return appleTesterIdentityRepository.save(identity);
    }

    @Transactional(readOnly = true)
    public List<AppleTesterIdentity> findPendingSyncIdentities() {
        return appleTesterIdentityRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                AppleTesterIdentityStatus.NEW,
                AppleTesterIdentityStatus.INVITATION_SENT,
                AppleTesterIdentityStatus.WAITING_ACCEPTANCE,
                AppleTesterIdentityStatus.USER_VISIBLE
        ));
    }

    private void requireIdentity(AppleTesterIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("AppleTesterIdentity is required");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("appleEmail is required");
        }
        return AppleTesterIdentity.normalizeEmail(email);
    }

    private String requireName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String normalized = value.trim();

        if (normalized.length() < 2) {
            throw new IllegalArgumentException(fieldName + " must be at least 2 characters");
        }

        if (normalized.length() > 120) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }

        return normalized;
    }
}