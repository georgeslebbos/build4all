package com.build4all.app.internaltesting.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequest;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequestStatus;
import com.build4all.app.internaltesting.dto.CreateIosInternalTestingRequestDto;
import com.build4all.app.internaltesting.dto.IosInternalTestingRequestResponseDto;
import com.build4all.app.internaltesting.integration.AppleInternalTestingCommand;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGateway;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayOutcome;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayResult;
import com.build4all.app.internaltesting.repository.IosInternalTestingRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class IosInternalTestingRequestService {

    private final IosInternalTestingRequestRepository requestRepository;
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final AdminUsersRepository adminUsersRepository;
    private final AppleInternalTestingGateway appleInternalTestingGateway;

    public IosInternalTestingRequestService(
            IosInternalTestingRequestRepository requestRepository,
            AdminUserProjectRepository adminUserProjectRepository,
            AdminUsersRepository adminUsersRepository,
            AppleInternalTestingGateway appleInternalTestingGateway
    ) {
        this.requestRepository = requestRepository;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.adminUsersRepository = adminUsersRepository;
        this.appleInternalTestingGateway = appleInternalTestingGateway;
    }

    public IosInternalTestingRequestResponseDto createRequest(
            Long requesterAdminId,
            Long ownerProjectLinkId,
            CreateIosInternalTestingRequestDto dto
    ) {
        if (requesterAdminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing requester admin id");
        }
        if (ownerProjectLinkId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerProjectLinkId is required");
        }
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String appleEmail = normalizeEmail(dto.appleEmail());
        String firstName = normalizeName(dto.firstName(), "First name");
        String lastName = normalizeName(dto.lastName(), "Last name");

        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        AdminUserProject link = resolveAccessibleLink(requester, requesterAdminId, ownerProjectLinkId);

        validateLinkForIosInternalTesting(link);

        Optional<IosInternalTestingRequest> existing = requestRepository
                .findTopByOwnerProjectLinkIdAndAppleEmailIgnoreCaseOrderByCreatedAtDesc(ownerProjectLinkId, appleEmail);

        if (existing.isPresent() && !existing.get().isFinalStatus()) {
            return toDto(existing.get());
        }

        IosInternalTestingRequest request = new IosInternalTestingRequest();
        request.setOwnerProjectLinkId(link.getId());
        request.setOwnerId(link.getAdmin().getAdminId());
        request.setProjectId(link.getProject().getId());
        request.setAppNameSnapshot(safeText(link.getAppName()));
        request.setBundleIdSnapshot(safeText(link.getIosBundleId()));
        request.setAppleEmail(appleEmail);
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setStatus(IosInternalTestingRequestStatus.REQUESTED);
        request.setLastError(null);
        request.setAppleUserId(null);
        request.setAppleInvitationId(null);
        request.setProcessedAt(null);
        request.setAcceptedAt(null);
        request.setReadyAt(null);

        IosInternalTestingRequest saved = requestRepository.save(request);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public IosInternalTestingRequestResponseDto getLatestRequest(
            Long requesterAdminId,
            Long ownerProjectLinkId
    ) {
        if (requesterAdminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing requester admin id");
        }
        if (ownerProjectLinkId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerProjectLinkId is required");
        }

        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        resolveAccessibleLink(requester, requesterAdminId, ownerProjectLinkId);

        IosInternalTestingRequest request = requestRepository
                .findTopByOwnerProjectLinkIdOrderByCreatedAtDesc(ownerProjectLinkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No iOS internal testing request found for this app"
                ));

        return toDto(request);
    }

    @Transactional(readOnly = true)
    public List<IosInternalTestingRequestResponseDto> listAllForSuperAdmin(Long requesterAdminId) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        return requestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public IosInternalTestingRequestResponseDto processRequest(Long requesterAdminId, Long requestId) {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }

        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (request.getStatus() == IosInternalTestingRequestStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancelled request cannot be processed");
        }

        if (request.getStatus() == IosInternalTestingRequestStatus.READY) {
            return toDto(request);
        }

        AdminUserProject link = adminUserProjectRepository.findById(request.getOwnerProjectLinkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App link not found"));
        
        if (link.getStatus() != null && link.getStatus().equalsIgnoreCase("DELETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This app is deleted and cannot be processed");
        }

        validateLinkForIosInternalTesting(link);

        request.setStatus(IosInternalTestingRequestStatus.PROCESSING);
        request.setProcessedAt(LocalDateTime.now());
        request.setLastError(null);

        try {
            AppleInternalTestingGatewayResult gatewayResult =
                    appleInternalTestingGateway.process(buildCommand(request));

            if (gatewayResult == null || gatewayResult.outcome() == null) {
                request.setStatus(IosInternalTestingRequestStatus.FAILED);
                request.setLastError("Apple gateway returned empty result");
                requestRepository.save(request);

                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Apple gateway returned empty result");
            }

            if (gatewayResult.outcome() == AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED) {
                request.setAppleUserId(gatewayResult.appleUserId());
                request.setAppleInvitationId(null);
                request.setAcceptedAt(LocalDateTime.now());
                request.setReadyAt(LocalDateTime.now());
                request.setStatus(IosInternalTestingRequestStatus.READY);
                request.setLastError(null);

                IosInternalTestingRequest saved = requestRepository.save(request);
                return toDto(saved);
            }

            if (gatewayResult.outcome() == AppleInternalTestingGatewayOutcome.INVITATION_SENT) {
                request.setAppleInvitationId(gatewayResult.invitationId());
                request.setStatus(IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE);
                request.setLastError(null);

                IosInternalTestingRequest saved = requestRepository.save(request);
                return toDto(saved);
            }

            request.setStatus(IosInternalTestingRequestStatus.FAILED);
            request.setLastError("Unsupported Apple gateway outcome");
            requestRepository.save(request);

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unsupported Apple gateway outcome");

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            request.setStatus(IosInternalTestingRequestStatus.FAILED);
            request.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            requestRepository.save(request);

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process iOS internal testing request");
        }
    }

    public int syncWaitingRequests() {
        List<IosInternalTestingRequest> waitingRequests = requestRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM,
                IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE
        ));

        int updatedCount = 0;

        for (IosInternalTestingRequest request : waitingRequests) {
            boolean updated = syncSingleWaitingRequestInternal(request);
            if (updated) {
                updatedCount++;
            }
        }

        return updatedCount;
    }

    public IosInternalTestingRequestResponseDto syncSingleRequest(Long requesterAdminId, Long requestId) {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }

        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        syncSingleWaitingRequestInternal(request);
        return toDto(request);
    }

    private boolean syncSingleWaitingRequestInternal(IosInternalTestingRequest request) {
        if (request.getStatus() != IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM
                && request.getStatus() != IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE) {
            return false;
        }

        if (isBlank(request.getAppleInvitationId())) {
            request.setStatus(IosInternalTestingRequestStatus.FAILED);
            request.setLastError("Missing apple invitation id for waiting request");
            requestRepository.save(request);
            return true;
        }

        try {
            AppleInternalTestingGatewayResult result =
                    appleInternalTestingGateway.syncInvitation(
                            buildCommand(request),
                            request.getAppleInvitationId()
                    );

            if (result == null || result.outcome() == null) {
                request.setLastError("Apple sync returned empty result");
                requestRepository.save(request);
                return false;
            }

            if (result.outcome() == AppleInternalTestingGatewayOutcome.STILL_WAITING) {
                request.setLastError(null);
                requestRepository.save(request);
                return false;
            }

            if (result.outcome() == AppleInternalTestingGatewayOutcome.INVITATION_ACCEPTED_AND_ADDED) {
                request.setStatus(IosInternalTestingRequestStatus.READY);
                request.setAppleUserId(result.appleUserId());
                request.setAcceptedAt(LocalDateTime.now());
                request.setReadyAt(LocalDateTime.now());
                request.setLastError(null);
                requestRepository.save(request);
                return true;
            }

            request.setLastError("Unsupported Apple sync outcome: " + result.outcome());
            requestRepository.save(request);
            return false;

        } catch (Exception ex) {
            request.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            requestRepository.save(request);
            return false;
        }
    }

    private AppleInternalTestingCommand buildCommand(IosInternalTestingRequest request) {
        return new AppleInternalTestingCommand(
                request.getId(),
                request.getOwnerProjectLinkId(),
                request.getOwnerId(),
                request.getProjectId(),
                request.getAppNameSnapshot(),
                request.getBundleIdSnapshot(),
                request.getAppleEmail(),
                request.getFirstName(),
                request.getLastName()
        );
    }

    private AdminUserProject resolveAccessibleLink(AdminUser requester, Long requesterAdminId, Long ownerProjectLinkId) {
        String roleName = requester.getRole() != null ? requester.getRole().getName() : null;

        if (roleName != null && roleName.equalsIgnoreCase("SUPER_ADMIN")) {
            return adminUserProjectRepository.findActiveById(ownerProjectLinkId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active app link not found"));
        }

        AdminUserProject link = adminUserProjectRepository.findByIdAndAdmin_AdminId(ownerProjectLinkId, requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have access to this app"
                ));

        String status = link.getStatus();
        if (status != null && status.equalsIgnoreCase("DELETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This app is deleted and cannot be used");
        }

        return link;
    }

    private void requireSuperAdmin(AdminUser requester) {
        String roleName = requester.getRole() != null ? requester.getRole().getName() : null;
        if (roleName == null || !roleName.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SUPER_ADMIN only");
        }
    }

    private void validateLinkForIosInternalTesting(AdminUserProject link) {
        if (link.getProject() == null || link.getProject().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This app is missing project data");
        }

        if (isBlank(link.getAppName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This app is missing app name");
        }

        if (isBlank(link.getIosBundleId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This app does not have an iOS bundle id yet"
            );
        }
    }

    private IosInternalTestingRequestResponseDto toDto(IosInternalTestingRequest request) {
        return new IosInternalTestingRequestResponseDto(
                request.getId(),
                request.getOwnerProjectLinkId(),
                request.getOwnerId(),
                request.getProjectId(),
                request.getAppNameSnapshot(),
                request.getBundleIdSnapshot(),
                request.getAppleEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getStatus(),
                request.getAppleUserId(),
                request.getAppleInvitationId(),
                request.getLastError(),
                request.getRequestedAt(),
                request.getProcessedAt(),
                request.getAcceptedAt(),
                request.getReadyAt(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Apple email is required");
        }
        return value.trim().toLowerCase();
    }

    private String normalizeName(String value, String fieldLabel) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldLabel + " is required");
        }

        String normalized = value.trim();
        if (normalized.length() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldLabel + " must be at least 2 characters"
            );
        }

        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldLabel + " is too long"
            );
        }

        return normalized;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}