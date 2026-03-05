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

    /* ========================= OWNER (tenant from token only) ========================= */

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/apps/me/access")
    public ResponseEntity<OwnerAppAccessResponse> getOwnerAccessMe(
            @RequestHeader("Authorization") String auth
    ) {
        Long aupId = jwtUtil.requireOwnerProjectId(auth);

        var app = aupRepo.findById(aupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND"));

        licensingService.ensureSubscriptionExists(app);

        OwnerAppAccessResponse res = licensingService.getOwnerDashboardAccess(aupId);
        return ResponseEntity.ok(res);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/apps/me/upgrade-request")
    public ResponseEntity<?> requestUpgradeMe(
            @RequestBody UpgradePlanRequest req,
            @RequestHeader("Authorization") String auth
    ) {
        Long aupId = jwtUtil.requireOwnerProjectId(auth);
        Long ownerId = jwtUtil.extractId(auth);

        var created = licensingService.createUpgradeRequest(aupId, req, ownerId);
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/apps/me/upgrade-requests")
    public ResponseEntity<?> listUpgradeRequestsForMe(
            @RequestHeader("Authorization") String auth
    ) {
        Long aupId = jwtUtil.requireOwnerProjectId(auth);
        return ResponseEntity.ok(upgradeReqRepo.findByAupIdOrderByRequestedAtDesc(aupId));
    }

    /* ========================= SUPER_ADMIN (act-as via aupId path) ========================= */

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/access")
    public ResponseEntity<OwnerAppAccessResponse> getOwnerAccess(
            @PathVariable Long aupId
    ) {
        var app = aupRepo.findById(aupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND"));

        licensingService.ensureSubscriptionExists(app);

        OwnerAppAccessResponse res = licensingService.getOwnerDashboardAccess(aupId);
        return ResponseEntity.ok(res);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/apps/{aupId}/upgrade-request")
    public ResponseEntity<?> requestUpgrade(
            @PathVariable Long aupId,
            @RequestBody UpgradePlanRequest req,
            @RequestHeader("Authorization") String auth
    ) {
        Long superAdminId = jwtUtil.extractId(auth);
        var created = licensingService.createUpgradeRequest(aupId, req, superAdminId);
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/upgrade-requests")
    public ResponseEntity<?> listUpgradeRequestsForApp(@PathVariable Long aupId) {
        return ResponseEntity.ok(upgradeReqRepo.findByAupIdOrderByRequestedAtDesc(aupId));
    }

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
                upgradeReqRepo.findPendingRows(PlanUpgradeRequestStatus.PENDING)
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