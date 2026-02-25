package com.build4all.tax.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.RegionRepository;
import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.service.LicensingService;
import com.build4all.security.JwtUtil;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.dto.TaxPreviewRequest;
import com.build4all.tax.dto.TaxRuleRequest;
import com.build4all.tax.dto.TaxRuleResponse;
import com.build4all.tax.service.TaxService;
import com.build4all.tax.service.impl.TaxServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tax")
@Tag(name = "Tax")
@SecurityRequirement(name = "bearerAuth")
public class TaxController {

    private final TaxService taxService;
    private final JwtUtil jwtUtil;
    private final LicensingService licensingService;

    private final AdminUserProjectRepository adminUserProjectRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;

    private final TaxServiceImpl taxServiceImpl;

    public TaxController(TaxService taxService,
            JwtUtil jwtUtil,
            AdminUserProjectRepository adminUserProjectRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository,
            TaxServiceImpl taxServiceImpl,
            LicensingService licensingService) {
this.taxService = taxService;
this.jwtUtil = jwtUtil;
this.adminUserProjectRepository = adminUserProjectRepository;
this.countryRepository = countryRepository;
this.regionRepository = regionRepository;
this.taxServiceImpl = taxServiceImpl;
this.licensingService = licensingService;
}
    /* ===================== helpers ===================== */
    
    
    private ResponseEntity<?> blockIfSubscriptionExceeded(Long ownerProjectId) {
        try {
            OwnerAppAccessResponse access = licensingService.getOwnerDashboardAccess(ownerProjectId);

            if (access == null || !access.isCanAccessDashboard()) {
                String reason = (access != null && access.getBlockingReason() != null)
                        ? access.getBlockingReason()
                        : "SUBSCRIPTION_LIMIT_EXCEEDED";

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Subscription limit exceeded. Upgrade your plan or reduce usage.",
                        "code", "SUBSCRIPTION_LIMIT_EXCEEDED",
                        "blockingReason", reason,
                        "ownerProjectId", ownerProjectId
                ));
            }

            return null;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Unable to validate subscription right now.",
                    "code", "SUBSCRIPTION_CHECK_UNAVAILABLE"
            ));
        }
    }

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private ResponseEntity<?> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg));
    }

    private ResponseEntity<?> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
    }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        String normalized = role.toUpperCase();
        for (String r : roles) {
            String expected = r.toUpperCase();
            if (normalized.equals(expected) || normalized.equals("ROLE_" + expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ✅ The ONLY source of tenant id.
     * If token is invalid or missing tenant claim => 401.
     */
    private Long tenantFromTokenOrThrow(String authHeader) {
        // JwtUtil.requireOwnerProjectId accepts raw or Bearer
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    /**
     * ✅ Convert request into entity using REAL lookups
     * BUT ownerProjectId is forced from token (client value is ignored).
     */
    private TaxRule fromRequestResolved(Long ownerProjectId, TaxRuleRequest req) {
        TaxRule rule = new TaxRule();

        AdminUserProject proj = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId: " + ownerProjectId));
        rule.setOwnerProject(proj);

        rule.setName(req.getName());
        rule.setRate(req.getRate());
        rule.setAppliesToShipping(req.isAppliesToShipping());
        rule.setEnabled(req.isEnabled());

        Country country = null;
        Region region = null;

        if (req.getCountryId() != null) {
            country = countryRepository.findById(req.getCountryId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid countryId: " + req.getCountryId()));
        }

        if (req.getRegionId() != null) {
            region = regionRepository.findById(req.getRegionId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid regionId: " + req.getRegionId()));
        }

        // Optional strict validation if Region has getCountry()
        if (country != null && region != null) {
            try {
                if (region.getCountry() != null && region.getCountry().getId() != null) {
                    if (!region.getCountry().getId().equals(country.getId())) {
                        throw new IllegalArgumentException("regionId does not belong to countryId");
                    }
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {
            }
        }

        rule.setCountry(country);
        rule.setRegion(region);

        return rule;
    }

    private TaxRuleResponse toResponse(TaxRule rule) {
        TaxRuleResponse r = new TaxRuleResponse();
        r.setId(rule.getId());
        r.setOwnerProjectId(rule.getOwnerProject() != null ? rule.getOwnerProject().getId() : null);
        r.setName(rule.getName());
        r.setRate(rule.getRate());
        r.setAppliesToShipping(rule.isAppliesToShipping());
        r.setEnabled(rule.isEnabled());
        r.setCountryId(rule.getCountry() != null ? rule.getCountry().getId() : null);
        r.setRegionId(rule.getRegion() != null ? rule.getRegion().getId() : null);
        return r;
    }

    /* ====================================================
       OWNER / SUPER_ADMIN: CRUD on Tax Rules
       ==================================================== */

    @PostMapping("/rules")
    @Operation(summary = "Create tax rule (OWNER/SUPER_ADMIN only)")
    public ResponseEntity<?> createRule(
            @RequestHeader("Authorization") String auth,
            @RequestBody TaxRuleRequest req
    ) {
        String token = strip(auth);
        
        

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return forbidden("Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token (Bearer ok)
            
            ResponseEntity<?> blocked = blockIfSubscriptionExceeded(ownerProjectId);
            if (blocked != null) return blocked;
            TaxRule created = taxService.createRule(fromRequestResolved(ownerProjectId, req));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
        } catch (RuntimeException ex) {
            // includes JwtUtil.requireOwnerProjectId errors
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update tax rule (OWNER/SUPER_ADMIN only)")
    public ResponseEntity<?> updateRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody TaxRuleRequest req
    ) {
        String token = strip(auth);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return forbidden("Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token

            TaxRule updates = fromRequestResolved(ownerProjectId, req);

            TaxRule updated = taxServiceImpl.updateRuleScoped(ownerProjectId, id, updates);
            return ResponseEntity.ok(toResponse(updated));

        } catch (RuntimeException ex) {
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }

    /**
     * ✅ IMPORTANT FIX:
     * Don’t return 204 (empty body) because Flutter often tries to parse JSON and explodes.
     * Return 200 with {message:"..."}.
     */
    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete tax rule (OWNER/SUPER_ADMIN only)")
    public ResponseEntity<?> deleteRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return forbidden("Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token

            taxServiceImpl.deleteRuleScoped(ownerProjectId, id);

            return ResponseEntity.ok(Map.of("message", "Tax rule deleted"));

        } catch (RuntimeException ex) {
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get tax rule by id (OWNER/SUPER_ADMIN only)")
    public ResponseEntity<?> getRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return forbidden("Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token
            TaxRule rule = taxServiceImpl.getRuleScoped(ownerProjectId, id);
            return ResponseEntity.ok(toResponse(rule));

        } catch (RuntimeException ex) {
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }

    @GetMapping("/rules")
    @Operation(summary = "List tax rules for tenant (OWNER/SUPER_ADMIN only)")
    public ResponseEntity<?> listRules(
            @RequestHeader("Authorization") String auth
    ) {
        String token = strip(auth);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return forbidden("Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token

            List<TaxRule> rules = taxService.listRulesByOwnerProject(ownerProjectId);
            List<TaxRuleResponse> resp = rules.stream().map(this::toResponse).collect(Collectors.toList());

            return ResponseEntity.ok(resp);

        } catch (RuntimeException ex) {
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }

    /* ====================================================
       USER / OWNER / SUPER_ADMIN: preview tax calculation
       ==================================================== */

    @PostMapping("/preview")
    @Operation(summary = "Preview item + shipping tax for given cart")
    public ResponseEntity<?> previewTax(
            @RequestHeader("Authorization") String auth,
            @RequestBody TaxPreviewRequest req
    ) {
        String token = strip(auth);

        if (!jwtUtil.validateToken(token)) {
            return unauthorized("Invalid token");
        }

        // ✅ include SUPER_ADMIN (your tokens use SUPER_ADMIN, not ADMIN)
        if (!hasRole(token, "USER", "OWNER", "SUPER_ADMIN")) {
            return forbidden("User/Owner/Super Admin role required");
        }

        try {
            Long ownerProjectId = tenantFromTokenOrThrow(auth); // ✅ from token (ignore request)

            BigDecimal itemTax = taxService.calculateItemTax(
                    ownerProjectId,
                    req.getAddress(),
                    req.getLines()
            );

            BigDecimal shippingTax = taxService.calculateShippingTax(
                    ownerProjectId,
                    req.getAddress(),
                    req.getShippingTotal()
            );

            return ResponseEntity.ok(Map.of(
                    "itemsTaxTotal", itemTax,
                    "shippingTaxTotal", shippingTax,
                    "totalTax", itemTax.add(shippingTax)
            ));

        } catch (RuntimeException ex) {
            return unauthorized(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
        }
    }
}
