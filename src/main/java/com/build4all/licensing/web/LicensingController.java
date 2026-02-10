package com.build4all.licensing.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.dto.UpdatePlanUsersAllowedRequest;
import com.build4all.licensing.dto.UpgradePlanRequest;
import com.build4all.licensing.repository.PlanCatalogRepository;
import com.build4all.licensing.repository.PlanUpgradeRequestRepository;
import com.build4all.licensing.service.LicensingService;
import com.build4all.security.JwtUtil;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/licensing")
public class LicensingController {

    private final LicensingService licensingService;
    private final AdminUserProjectRepository aupRepo;
    private final PlanCatalogRepository planRepo;
    private final PlanUpgradeRequestRepository upgradeReqRepo;
    private final JwtUtil jwtUtil;


    public LicensingController(LicensingService licensingService, AdminUserProjectRepository aupRepo,PlanCatalogRepository planRepo,PlanUpgradeRequestRepository upgradeReqRepo,JwtUtil jwtUtil) {
        this.licensingService = licensingService;
        this.aupRepo = aupRepo;
        this.planRepo = planRepo;
        this.upgradeReqRepo=upgradeReqRepo;
        this.jwtUtil=jwtUtil;
        
    }

    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/access")
    public ResponseEntity<OwnerAppAccessResponse> getOwnerAccess(@PathVariable Long aupId) {

        // ✅ ensures FREE subscription exists if missing
        var app = aupRepo.findById(aupId)
                .orElseThrow(() -> new RuntimeException("AdminUserProject not found: " + aupId));
        licensingService.ensureSubscriptionExists(app);

        OwnerAppAccessResponse res = licensingService.getOwnerDashboardAccess(aupId);
        return ResponseEntity.ok(res);
    }
    
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PostMapping("/apps/{aupId}/upgrade")
    public ResponseEntity<?> upgrade(@PathVariable Long aupId,
                                    @RequestBody UpgradePlanRequest req) {

        licensingService.upgradeSubscription(aupId, req);

      
        OwnerAppAccessResponse access = licensingService.getOwnerDashboardAccess(aupId);
        return ResponseEntity.ok(access);
    }
    
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/plans")
    public ResponseEntity<?> listPlans() {
        return ResponseEntity.ok(planRepo.findAll());
    }
    
    
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/plans/{planCode}/users-allowed")
    public ResponseEntity<?> updatePlanUsersAllowed(@PathVariable PlanCode planCode,
                                                    @RequestBody UpdatePlanUsersAllowedRequest req) {
        var updated = licensingService.updatePlanUsersAllowed(planCode, req);
        return ResponseEntity.ok(updated);
    }
    
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @PostMapping("/apps/{aupId}/upgrade-request")
    public ResponseEntity<?> requestUpgrade(@PathVariable Long aupId,
                                           @RequestBody UpgradePlanRequest req,
                                           @RequestHeader("Authorization") String auth) {

        Long userId = jwtUtil.extractId(auth.replace("Bearer ", ""));
        var created = licensingService.createUpgradeRequest(aupId, req, userId);
        return ResponseEntity.ok(created);
    }
    
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @GetMapping("/apps/{aupId}/upgrade-requests")
    public ResponseEntity<?> listUpgradeRequestsForApp(@PathVariable Long aupId) {
        return ResponseEntity.ok(upgradeReqRepo.findByAupIdOrderByRequestedAtDesc(aupId));
    }


}

