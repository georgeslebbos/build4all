package com.build4all.app.internaltesting.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.app.internaltesting.domain.AppleTesterIdentity;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequest;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequestStatus;
import com.build4all.app.internaltesting.dto.CreateIosInternalTestingRequestDto;
import com.build4all.app.internaltesting.dto.IosInternalTestingRequestResponseDto;
import com.build4all.app.internaltesting.integration.AppleInternalTestingCommand;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGateway;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayOutcome;
import com.build4all.app.internaltesting.integration.AppleInternalTestingGatewayResult;
import com.build4all.app.internaltesting.repository.IosInternalTestingRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class IosInternalTestingRequestService {

    private final IosInternalTestingRequestRepository requestRepository;
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final AdminUsersRepository adminUsersRepository;
    private final AppleInternalTestingGateway appleInternalTestingGateway;
    private final AppleTesterIdentityService appleTesterIdentityService;
    private final boolean autoProcessOnCreate;

    private static final int MAX_INTERNAL_TESTERS_PER_APP = 10;

    private static final Set<IosInternalTestingRequestStatus> SLOT_CONSUMING_STATUSES = Set.of(
            IosInternalTestingRequestStatus.REQUESTED,
            IosInternalTestingRequestStatus.PROCESSING,
            IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM,
            IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE,
            IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC,
            IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING,
            IosInternalTestingRequestStatus.MANUAL_REVIEW_REQUIRED,
            IosInternalTestingRequestStatus.READY
    );

    public IosInternalTestingRequestService(
            IosInternalTestingRequestRepository requestRepository,
            AdminUserProjectRepository adminUserProjectRepository,
            AdminUsersRepository adminUsersRepository,
            AppleInternalTestingGateway appleInternalTestingGateway,
            AppleTesterIdentityService appleTesterIdentityService,
            @Value("${build4all.ios-internal.auto-process-on-create:true}") boolean autoProcessOnCreate
    ) {
        this.requestRepository = requestRepository;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.adminUsersRepository = adminUsersRepository;
        this.appleInternalTestingGateway = appleInternalTestingGateway;
        this.appleTesterIdentityService = appleTesterIdentityService;
        this.autoProcessOnCreate = autoProcessOnCreate;
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
                .findTopByOwnerProjectLinkIdAndAppleEmailIgnoreCaseOrderByCreatedAtDesc(link.getId(), appleEmail);

        if (existing.isPresent()) {
            IosInternalTestingRequest current = existing.get();

            if (current.getStatus() == IosInternalTestingRequestStatus.READY) {
                return toDto(current);
            }

            if (!current.isFinalStatus()) {
                return toDto(current);
            }
        }

        long usedSlots = countUsedSlotsForApp(link.getId());
        if (usedSlots >= MAX_INTERNAL_TESTERS_PER_APP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This app already reached the internal testing capacity of " + MAX_INTERNAL_TESTERS_PER_APP
            );
        }

        AppleTesterIdentity identity = appleTesterIdentityService.findOrCreateIdentity(
                appleEmail,
                firstName,
                lastName
        );

        IosInternalTestingRequest request = new IosInternalTestingRequest();
        request.setOwnerProjectLinkId(link.getId());
        request.setOwnerId(link.getAdmin().getAdminId());
        request.setProjectId(link.getProject().getId());
        request.setAppNameSnapshot(safeText(link.getAppName()));
        request.setBundleIdSnapshot(safeText(link.getIosBundleId()));
        request.setAppleEmail(appleEmail);
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setAppleTesterIdentity(identity);
        request.setStatus(IosInternalTestingRequestStatus.REQUESTED);
        request.setLastError(null);
        request.setAppleUserId(null);
        request.setAppleInvitationId(null);
        request.setProcessedAt(null);
        request.setAcceptedAt(null);
        request.setReadyAt(null);

        IosInternalTestingRequest saved = requestRepository.save(request);

        if (!autoProcessOnCreate) {
            return toDto(saved);
        }

        try {
            return processRequestInternal(saved.getId());
        } catch (Exception ex) {
            IosInternalTestingRequest refreshed = requestRepository.findById(saved.getId()).orElse(saved);
            return toDto(refreshed);
        }
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

        List<IosInternalTestingRequest> requests = requestRepository.findByOwnerProjectLinkId(
                ownerProjectLinkId,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        IosInternalTestingRequest request = requests.stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No iOS internal testing request found for this app"
                ));

        return toDto(request);
    }

    @Transactional(readOnly = true)
    public List<IosInternalTestingRequestResponseDto> listRequestsForApp(
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

        AdminUserProject link = resolveAccessibleLink(requester, requesterAdminId, ownerProjectLinkId);

        return requestRepository.findByOwnerProjectLinkId(
                        link.getId(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUsedSlotsForApp(Long requesterAdminId, Long ownerProjectLinkId) {
        if (requesterAdminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing requester admin id");
        }
        if (ownerProjectLinkId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerProjectLinkId is required");
        }

        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        AdminUserProject link = resolveAccessibleLink(requester, requesterAdminId, ownerProjectLinkId);

        return countUsedSlotsForApp(link.getId());
    }

    @Transactional(readOnly = true)
    public int getMaxSlotsPerApp() {
        return MAX_INTERNAL_TESTERS_PER_APP;
    }

    @Transactional(readOnly = true)
    public List<IosInternalTestingRequestResponseDto> listAllForSuperAdmin(
            Long requesterAdminId,
            String status,
            boolean manualOnly
    ) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        List<IosInternalTestingRequest> all = requestRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return all.stream()
                .filter(r -> {
                    if (manualOnly) {
                        return r.getStatus() == IosInternalTestingRequestStatus.MANUAL_REVIEW_REQUIRED
                                || r.getStatus() == IosInternalTestingRequestStatus.FAILED;
                    }
                    return true;
                })
                .filter(r -> {
                    if (status == null || status.isBlank()) {
                        return true;
                    }
                    return r.getStatus().name().equalsIgnoreCase(status.trim());
                })
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public IosInternalTestingRequestResponseDto getRequestForSuperAdmin(Long requesterAdminId, Long requestId) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        return toDto(request);
    }

    public int syncAllForSuperAdmin(Long requesterAdminId) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        return syncWaitingRequests();
    }

    public IosInternalTestingRequestResponseDto markManualReviewRequired(
            Long requesterAdminId,
            Long requestId,
            String note
    ) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        request.setStatus(IosInternalTestingRequestStatus.MANUAL_REVIEW_REQUIRED);
        request.setLastError(
                (note != null && !note.isBlank())
                        ? note.trim()
                        : "Manual super-admin review required"
        );

        IosInternalTestingRequest saved = requestRepository.save(request);
        return toDto(saved);
    }

    public IosInternalTestingRequestResponseDto markReadyManually(
            Long requesterAdminId,
            Long requestId,
            String appleUserId,
            String note
    ) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        AppleTesterIdentity identity = ensureIdentityAttached(request);

        request.setStatus(IosInternalTestingRequestStatus.READY);

        if (appleUserId != null && !appleUserId.isBlank()) {
            request.setAppleUserId(appleUserId.trim());
            appleTesterIdentityService.markUserVisible(identity, appleUserId.trim());
        }

        if (request.getAcceptedAt() == null) {
            request.setAcceptedAt(LocalDateTime.now());
        }

        request.setReadyAt(LocalDateTime.now());
        request.setLastError(note != null && !note.isBlank() ? note.trim() : null);

        IosInternalTestingRequest saved = requestRepository.save(request);
        return toDto(saved);
    }

    public IosInternalTestingRequestResponseDto cancelRequestForSuperAdmin(
            Long requesterAdminId,
            Long requestId,
            String note
    ) {
        AdminUser requester = adminUsersRepository.findByAdminId(requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Requester not found"));

        requireSuperAdmin(requester);

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        request.setStatus(IosInternalTestingRequestStatus.CANCELLED);

        if (note != null && !note.isBlank()) {
            request.setLastError(note.trim());
        }

        IosInternalTestingRequest saved = requestRepository.save(request);
        return toDto(saved);
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

        return processRequestInternal(requestId);
    }

    public int syncWaitingRequests() {
        List<IosInternalTestingRequest> waitingRequests = requestRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM,
                IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE,
                IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC,
                IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING
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

        IosInternalTestingRequest refreshed = requestRepository.findById(requestId).orElse(request);
        return toDto(refreshed);
    }
    
    
    public int runBackgroundSyncCycle() {
        int updatedCount = 0;

        List<IosInternalTestingRequest> queuedRequests =
                requestRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                        IosInternalTestingRequestStatus.REQUESTED,
                        IosInternalTestingRequestStatus.PROCESSING
                ));

        System.out.println("BACKGROUND queuedRequests count => " + queuedRequests.size());

        for (IosInternalTestingRequest request : queuedRequests) {
            try {
                System.out.println("BACKGROUND processing requestId => " + request.getId()
                        + ", status => " + request.getStatus());

                processRequestInternal(request.getId());
                updatedCount++;

            } catch (Exception ex) {
                System.out.println("❌ runBackgroundSyncCycle process failed for requestId=" + request.getId());
                System.out.println("❌ error => " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        }

        List<IosInternalTestingRequest> waitingRequests =
                requestRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                        IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM,
                        IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE,
                        IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC,
                        IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING
                ));

        System.out.println("BACKGROUND waitingRequests count => " + waitingRequests.size());

        for (IosInternalTestingRequest request : waitingRequests) {
            try {
                System.out.println("BACKGROUND syncing requestId => " + request.getId()
                        + ", status => " + request.getStatus());

                boolean updated = syncSingleWaitingRequestInternal(request);

                System.out.println("BACKGROUND sync result requestId => " + request.getId()
                        + ", updated => " + updated);

                if (updated) {
                    updatedCount++;
                }

            } catch (Exception ex) {
                System.out.println("❌ runBackgroundSyncCycle sync failed for requestId=" + request.getId());
                System.out.println("❌ error => " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        }

        System.out.println("BACKGROUND total updatedCount => " + updatedCount);
        return updatedCount;
    }

    private IosInternalTestingRequestResponseDto processRequestInternal(Long requestId) {
        if (requestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }

        IosInternalTestingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (request.getStatus() == IosInternalTestingRequestStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancelled request cannot be processed");
        }

        if (request.getStatus() == IosInternalTestingRequestStatus.READY) {
            return toDto(request);
        }

        if (request.getStatus() == IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE
                || request.getStatus() == IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM
                || request.getStatus() == IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC
                || request.getStatus() == IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING) {

            syncSingleWaitingRequestInternal(request);

            IosInternalTestingRequest refreshed = requestRepository.findById(requestId).orElse(request);
            return toDto(refreshed);
        }

        AdminUserProject link = adminUserProjectRepository.findById(request.getOwnerProjectLinkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App link not found"));

        if (link.getStatus() != null && link.getStatus().equalsIgnoreCase("DELETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This app is deleted and cannot be processed");
        }

        validateLinkForIosInternalTesting(link);

        AppleTesterIdentity identity = ensureIdentityAttached(request);

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

            appleTesterIdentityService.applyGatewayResult(identity, gatewayResult);

            if (gatewayResult.outcome() == AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED) {
                request.setAppleUserId(gatewayResult.appleUserId());
                request.setAppleInvitationId(gatewayResult.invitationId());

                if (request.getAcceptedAt() == null) {
                    request.setAcceptedAt(LocalDateTime.now());
                }

                request.setReadyAt(LocalDateTime.now());
                request.setStatus(IosInternalTestingRequestStatus.READY);
                request.setLastError(null);

                IosInternalTestingRequest saved = requestRepository.save(request);
                return toDto(saved);
            }

            if (gatewayResult.outcome() == AppleInternalTestingGatewayOutcome.WAITING_FOR_APPLE_USER_SYNC) {
                request.setAppleUserId(gatewayResult.appleUserId());
                request.setAppleInvitationId(gatewayResult.invitationId());
                request.setStatus(IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC);
                request.setLastError(null);

                IosInternalTestingRequest saved = requestRepository.save(request);
                return toDto(saved);
            }

            if (gatewayResult.outcome() == AppleInternalTestingGatewayOutcome.INTERNAL_ACCESS_PENDING) {
                request.setAppleUserId(gatewayResult.appleUserId());
                request.setAppleInvitationId(gatewayResult.invitationId());
                request.setLastError(null);
                request.setProcessedAt(LocalDateTime.now());

                boolean hasApplePresence =
                        gatewayResult.appleUserId() != null && !gatewayResult.appleUserId().isBlank();

                boolean hasBetaTesterPresence =
                        gatewayResult.appleBetaTesterId() != null && !gatewayResult.appleBetaTesterId().isBlank();

                if (hasApplePresence || hasBetaTesterPresence) {
                    request.setStatus(IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING);

                    if (request.getAcceptedAt() == null) {
                        request.setAcceptedAt(LocalDateTime.now());
                    }
                } else {
                    request.setStatus(IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC);
                }

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
            String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

            if (errorMessage.contains("APPLE_TESTER_CANNOT_BE_ASSIGNED_TO_INTERNAL_GROUP")) {
                request.setStatus(IosInternalTestingRequestStatus.MANUAL_REVIEW_REQUIRED);
                request.setLastError(errorMessage);
                requestRepository.save(request);

                appleTesterIdentityService.markFailed(identity, errorMessage);

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Apple rejected assigning this tester to the internal testing group"
                );
            }

            request.setStatus(IosInternalTestingRequestStatus.FAILED);
            request.setLastError(errorMessage);
            requestRepository.save(request);

            appleTesterIdentityService.markFailed(identity, errorMessage);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process iOS internal testing request"
            );
        }
    }

    private boolean syncSingleWaitingRequestInternal(IosInternalTestingRequest request) {
        if (request.getStatus() != IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM
                && request.getStatus() != IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE
                && request.getStatus() != IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC
                && request.getStatus() != IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING) {
            return false;
        }

        // IMPORTANT FIX:
        // If this request has no invitation and no Apple user,
        // it is stuck in the wrong waiting state.
        // Reset it to REQUESTED and restart the full flow,
        // so process() can send the Apple team invitation if needed.
        boolean noInvitation = request.getAppleInvitationId() == null || request.getAppleInvitationId().isBlank();
        boolean noAppleUser = request.getAppleUserId() == null || request.getAppleUserId().isBlank();

        if (noInvitation && noAppleUser) {
            System.out.println("Request " + request.getId() + " has no invitationId and no appleUserId => restart full process");

            request.setStatus(IosInternalTestingRequestStatus.REQUESTED);
            request.setLastError("Restarted full process because request was stuck without invitationId and without appleUserId");
            requestRepository.save(request);

            processRequestInternal(request.getId());
            return true;
        }

        if ((request.getStatus() == IosInternalTestingRequestStatus.INVITED_TO_APPLE_TEAM
                || request.getStatus() == IosInternalTestingRequestStatus.WAITING_OWNER_ACCEPTANCE)
                && isBlank(request.getAppleInvitationId())) {
            request.setStatus(IosInternalTestingRequestStatus.FAILED);
            request.setLastError("Missing apple invitation id for waiting request");
            requestRepository.save(request);
            return true;
        }

        AppleTesterIdentity identity = ensureIdentityAttached(request);

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

            appleTesterIdentityService.applyGatewayResult(identity, result);

            if (result.outcome() == AppleInternalTestingGatewayOutcome.STILL_WAITING) {
                request.setLastError(null);
                requestRepository.save(request);
                return false;
            }

            if (result.outcome() == AppleInternalTestingGatewayOutcome.WAITING_FOR_APPLE_USER_SYNC) {
                request.setStatus(IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC);
                request.setAppleUserId(result.appleUserId());
                request.setAppleInvitationId(result.invitationId());
                request.setLastError(null);
                requestRepository.save(request);
                return false;
            }

            if (result.outcome() == AppleInternalTestingGatewayOutcome.INTERNAL_ACCESS_PENDING) {
                request.setAppleUserId(result.appleUserId());
                request.setAppleInvitationId(result.invitationId());
                request.setLastError(null);

                boolean hasApplePresence =
                        result.appleUserId() != null && !result.appleUserId().isBlank();

                boolean hasBetaTesterPresence =
                        result.appleBetaTesterId() != null && !result.appleBetaTesterId().isBlank();

                if (hasApplePresence || hasBetaTesterPresence) {
                    request.setStatus(IosInternalTestingRequestStatus.ADDING_TO_INTERNAL_TESTING);

                    if (request.getAcceptedAt() == null) {
                        request.setAcceptedAt(LocalDateTime.now());
                    }
                } else {
                    request.setStatus(IosInternalTestingRequestStatus.WAITING_APPLE_USER_SYNC);
                }

                requestRepository.save(request);
                return false;
            }

            if (result.outcome() == AppleInternalTestingGatewayOutcome.INVITATION_ACCEPTED_AND_ADDED
                    || result.outcome() == AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED) {

                request.setStatus(IosInternalTestingRequestStatus.READY);
                request.setAppleUserId(result.appleUserId());
                request.setAppleInvitationId(result.invitationId());

                if (request.getAcceptedAt() == null) {
                    request.setAcceptedAt(LocalDateTime.now());
                }

                request.setReadyAt(LocalDateTime.now());
                request.setLastError(null);
                requestRepository.save(request);
                return true;
            }

            request.setLastError("Unsupported Apple sync outcome: " + result.outcome());
            requestRepository.save(request);
            return false;

        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

            if (msg.contains("APPLE_TESTER_CANNOT_BE_ASSIGNED_TO_INTERNAL_GROUP")) {
                request.setStatus(IosInternalTestingRequestStatus.MANUAL_REVIEW_REQUIRED);
                request.setLastError(msg);
                requestRepository.save(request);

                appleTesterIdentityService.markFailed(identity, msg);

                System.out.println("❌ syncSingleWaitingRequestInternal manual review required for requestId=" + request.getId());
                System.out.println("❌ sync error => " + msg);
                ex.printStackTrace();

                return true;
            }

            request.setLastError(msg);
            requestRepository.save(request);

            appleTesterIdentityService.markFailed(identity, msg);

            System.out.println("❌ syncSingleWaitingRequestInternal failed for requestId=" + request.getId());
            System.out.println("❌ sync error => " + msg);
            ex.printStackTrace();

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
            return adminUserProjectRepository.findById(ownerProjectLinkId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "App link not found"));
        }

        AdminUserProject link = adminUserProjectRepository.findByIdAndAdmin_AdminId(ownerProjectLinkId, requesterAdminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have access to this app"
                ));

        if (link.getStatus() != null && link.getStatus().equalsIgnoreCase("DELETED")) {
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

    private long countUsedSlotsForApp(Long ownerProjectLinkId) {
        return requestRepository.countByOwnerProjectLinkIdAndStatusIn(
                ownerProjectLinkId,
                SLOT_CONSUMING_STATUSES
        );
    }

    private AppleTesterIdentity ensureIdentityAttached(IosInternalTestingRequest request) {
        if (request.getAppleTesterIdentity() != null) {
            return request.getAppleTesterIdentity();
        }

        AppleTesterIdentity identity = appleTesterIdentityService.findOrCreateIdentity(
                request.getAppleEmail(),
                request.getFirstName(),
                request.getLastName()
        );

        request.setAppleTesterIdentity(identity);
        requestRepository.save(request);

        return identity;
    }

    private IosInternalTestingRequestResponseDto toDto(IosInternalTestingRequest request) {
        Long identityId = null;
        String betaTesterId = null;
        com.build4all.app.internaltesting.domain.AppleTesterIdentityStatus identityStatus = null;

        if (request.getAppleTesterIdentity() != null) {
            identityId = request.getAppleTesterIdentity().getId();
            betaTesterId = request.getAppleTesterIdentity().getAppleBetaTesterId();
            identityStatus = request.getAppleTesterIdentity().getStatus();
        }

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
                request.getUpdatedAt(),
                identityId,
                betaTesterId,
                identityStatus
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

        if (normalized.length() > 120) {
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