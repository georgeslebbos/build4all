package com.build4all.licensing.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.domain.PlanUpgradeRequestStatus;
import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.dto.RejectUpgradeRequest;
import com.build4all.licensing.dto.UpdatePlanUsersAllowedRequest;
import com.build4all.licensing.dto.UpgradePlanRequest;
import com.build4all.licensing.repository.PlanCatalogRepository;
import com.build4all.licensing.repository.PlanUpgradeRequestRepository;
import com.build4all.licensing.service.LicensingService;
import com.build4all.security.JwtUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/licensing")
public class LicensingController {

    private final LicensingService licensingService;
    private final AdminUserProjectRepository aupRepo;
    private final PlanCatalogRepository planRepo;
    private final PlanUpgradeRequestRepository upgradeReqRepo;
    private final JwtUtil jwtUtil;

    public LicensingController(
            LicensingService licensingService,
            AdminUserProjectRepository aupRepo,
            PlanCatalogRepository planRepo,
            PlanUpgradeRequestRepository upgradeReqRepo,
            JwtUtil jwtUtil
    ) {
        this.licensingService = licensingService;
        this.aupRepo = aupRepo;
        this.planRepo = planRepo;
        this.upgradeReqRepo = upgradeReqRepo;
        this.jwtUtil = jwtUtil;
    }

    /**
     * ✅ SECURITY GUARD:
     * - If caller is OWNER => must only access their own aupId (token.ownerProjectId == aupId)
     * - If caller is SUPER_ADMIN => allow any aupId
     *
     * Uses JwtUtil tenant claim instead of relying on DB owner linkage (clean + fast).
     */
    private void enforceOwnerScopeIfNeeded(Long aupId, String authHeader) {
        String role = jwtUtil.extractRole(authHeader);

        if (role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        }

        // OWNER is tenant-scoped: lock them to their own AUP only
        if ("OWNER".equalsIgnoreCase(role)) {
            try {
                jwtUtil.requireTenantMatch(authHeader, aupId);
            } catch (RuntimeException ex) {
                // Use 404 to avoid leaking that other AUP ids exist (anti-enumeration)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND");
            }
        }
    }

    // ========================= OWNER / SUPER_ADMIN =========================

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/access")
    public ResponseEntity<OwnerAppAccessResponse> getOwnerAccess(
            @PathVariable Long aupId,
            @RequestHeader("Authorization") String auth
    ) {
        enforceOwnerScopeIfNeeded(aupId, auth);

        var app = aupRepo.findById(aupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND"));

        // ensures FREE subscription exists if missing
        licensingService.ensureSubscriptionExists(app);

        OwnerAppAccessResponse res = licensingService.getOwnerDashboardAccess(aupId);
        return ResponseEntity.ok(res);
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PostMapping("/apps/{aupId}/upgrade-request")
    public ResponseEntity<?> requestUpgrade(
            @PathVariable Long aupId,
            @RequestBody UpgradePlanRequest req,
            @RequestHeader("Authorization") String auth
    ) {
        enforceOwnerScopeIfNeeded(aupId, auth);

        Long userId = jwtUtil.extractId(auth);
        var created = licensingService.createUpgradeRequest(aupId, req, userId);
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/upgrade-requests")
    public ResponseEntity<?> listUpgradeRequestsForApp(
            @PathVariable Long aupId,
            @RequestHeader("Authorization") String auth
    ) {
        enforceOwnerScopeIfNeeded(aupId, auth);
        return ResponseEntity.ok(upgradeReqRepo.findByAupIdOrderByRequestedAtDesc(aupId));
    }

    // ========================= SUPER_ADMIN ONLY =========================

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/apps/{aupId}/upgrade")
    public ResponseEntity<?> upgrade(
            @PathVariable Long aupId,
            @RequestBody UpgradePlanRequest req
    ) {
        licensingService.upgradeSubscription(aupId, req);
        return ResponseEntity.ok(licensingService.getOwnerDashboardAccess(aupId));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/plans")
    public ResponseEntity<?> listPlans() {
        return ResponseEntity.ok(planRepo.findAll());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/plans/{planCode}/users-allowed")
    public ResponseEntity<?> updatePlanUsersAllowed(
            @PathVariable PlanCode planCode,
            @RequestBody UpdatePlanUsersAllowedRequest req
    ) {
        var updated = licensingService.updatePlanUsersAllowed(planCode, req);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/upgrade-requests/pending")
    public ResponseEntity<?> listPendingUpgradeRequests() {
        return ResponseEntity.ok(
                upgradeReqRepo.findByStatusOrderByRequestedAtAsc(PlanUpgradeRequestStatus.PENDING)
        );
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/upgrade-requests/{requestId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long requestId,
            @RequestHeader("Authorization") String auth
    ) {
        Long superAdminId = jwtUtil.extractId(auth);
        return ResponseEntity.ok(licensingService.approveUpgradeRequest(requestId, superAdminId));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/upgrade-requests/{requestId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long requestId,
            @RequestBody RejectUpgradeRequest body,
            @RequestHeader("Authorization") String auth
    ) {
        Long superAdminId = jwtUtil.extractId(auth);
        String note = (body != null) ? body.getNote() : null;
        return ResponseEntity.ok(licensingService.rejectUpgradeRequest(requestId, superAdminId, note));
    }
}
