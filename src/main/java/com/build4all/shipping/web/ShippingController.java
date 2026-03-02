package com.build4all.shipping.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.RegionRepository;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import com.build4all.security.JwtUtil;
import com.build4all.shipping.domain.ShippingMethod;
import com.build4all.shipping.domain.ShippingMethodType;
import com.build4all.shipping.dto.ShippingMethodRequest;
import com.build4all.shipping.dto.ShippingMethodResponse;
import com.build4all.shipping.dto.ShippingQuote;
import com.build4all.shipping.dto.ShippingQuoteRequest;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.shipping.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipping")
@Tag(name = "Shipping")
@SecurityRequirement(name = "bearerAuth")
public class ShippingController {

    private final ShippingService shippingService;
    private final ShippingMethodRepository methodRepository;
    private final JwtUtil jwtUtil;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;

    private final AdminUserProjectRepository adminUserProjectRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;

    public ShippingController(
            ShippingService shippingService,
            ShippingMethodRepository methodRepository,
            JwtUtil jwtUtil,
            AdminUserProjectRepository adminUserProjectRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository,
            OwnerSubscriptionGuard ownerSubscriptionGuard
    ) {
        this.shippingService = shippingService;
        this.methodRepository = methodRepository;
        this.jwtUtil = jwtUtil;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.countryRepository = countryRepository;
        this.regionRepository = regionRepository;
        this.ownerSubscriptionGuard = ownerSubscriptionGuard;
    }

    /* ===================== helpers ===================== */

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private boolean hasRole(String tokenOrBearer, String... roles) {
        String jwt = strip(tokenOrBearer);
        String role = jwtUtil.extractRole(jwt);
        if (role == null) return false;

        String normalized = role.toUpperCase();
        for (String r : roles) {
            String expected = r.toUpperCase();
            if (normalized.equals(expected) || normalized.equals("ROLE_" + expected)) return true;
        }
        return false;
    }

    private Long safeTenantFromToken(String auth) {
        try {
            return jwtUtil.extractOwnerProjectIdClaim(strip(auth));
        } catch (Exception e) {
            return null;
        }
    }

    /* ===================== mapping helpers ===================== */

    private ShippingMethodResponse toResponse(ShippingMethod m) {
        ShippingMethodResponse r = new ShippingMethodResponse();
        r.setId(m.getId());
        r.setOwnerProjectId(m.getOwnerProject() != null ? m.getOwnerProject().getId() : null);
        r.setName(m.getName());
        r.setDescription(m.getDescription());
        r.setMethodType(m.getType() != null ? m.getType().name() : null);
        r.setFlatRate(m.getFlatRate());
        r.setPricePerKg(m.getPricePerKg());
        r.setFreeShippingThreshold(m.getFreeShippingThreshold());
        r.setEnabled(m.isEnabled());
        r.setCountryId(m.getCountry() != null ? m.getCountry().getId() : null);
        r.setRegionId(m.getRegion() != null ? m.getRegion().getId() : null);
        return r;
    }

    private void applyRequestToMethodCore(ShippingMethodRequest req, ShippingMethod m) {
        if (req.getName() != null) m.setName(req.getName());
        if (req.getDescription() != null) m.setDescription(req.getDescription());

        if (req.getMethodType() != null && !req.getMethodType().isBlank()) {
            ShippingMethodType type = ShippingMethodType.valueOf(req.getMethodType().trim().toUpperCase());
            m.setType(type);
        }

        if (req.getFlatRate() != null) m.setFlatRate(req.getFlatRate());
        if (req.getPricePerKg() != null) m.setPricePerKg(req.getPricePerKg());
        if (req.getFreeShippingThreshold() != null) m.setFreeShippingThreshold(req.getFreeShippingThreshold());

        m.setEnabled(req.isEnabled());
    }

    private void applyCountryRegion(ShippingMethodRequest req, ShippingMethod m) {
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

        // ✅ If you actually have region.getCountry(), validate normally (recommended).
        // If you don't, remove this block entirely.
        if (country != null && region != null && region.getCountry() != null && region.getCountry().getId() != null) {
            if (!region.getCountry().getId().equals(country.getId())) {
                throw new IllegalArgumentException("regionId does not belong to countryId");
            }
        }

        m.setCountry(country);
        m.setRegion(region);
    }

    /* ====================================================
       Checkout-time endpoints
       ==================================================== */

    @PostMapping("/quote")
    @Operation(summary = "Get default shipping quote for cart")
    public ResponseEntity<?> quote(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingQuoteRequest req
    ) {
        // If you want to support guests later, remove this auth gate.
        if (!hasRole(auth, "USER", "BUSINESS", "OWNER", "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Unauthorized role"));
        }

        Long tokenTenant = safeTenantFromToken(auth);
        if (tokenTenant != null) {
            if (req.getOwnerProjectId() == null || !tokenTenant.equals(req.getOwnerProjectId())) {
                throw new IllegalArgumentException("Tenant mismatch");
            }
        }

        ShippingQuote quote = shippingService.getQuote(
                req.getOwnerProjectId(),
                req.getAddress(),
                req.getLines()
        );
        return ResponseEntity.ok(quote);
    }

    @PostMapping("/available-methods")
    @Operation(summary = "List available shipping methods + prices for cart")
    public ResponseEntity<?> availableMethods(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingQuoteRequest req
    ) {
        if (!hasRole(auth, "USER", "BUSINESS", "OWNER", "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Unauthorized role"));
        }

        Long tokenTenant = safeTenantFromToken(auth);
        if (tokenTenant != null) {
            if (req.getOwnerProjectId() == null || !tokenTenant.equals(req.getOwnerProjectId())) {
                throw new IllegalArgumentException("Tenant mismatch");
            }
        }

        List<ShippingQuote> list = shippingService.getAvailableMethods(
                req.getOwnerProjectId(),
                req.getAddress(),
                req.getLines()
        );

        return ResponseEntity.ok(list);
    }

    /* ====================================================
       OWNER / SUPER_ADMIN: CRUD
       ==================================================== */

    @PostMapping("/methods")
    @Operation(summary = "Create shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> createMethod(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingMethodRequest req
    ) {
        Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth);

        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        if (req.getName() == null || req.getName().isBlank()) throw new IllegalArgumentException("name is required");
        if (req.getMethodType() == null || req.getMethodType().isBlank()) throw new IllegalArgumentException("methodType is required");

        AdminUserProject ownerProject = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId in token: " + ownerProjectId));

        ShippingMethod method = new ShippingMethod();
        method.setOwnerProject(ownerProject);

        applyRequestToMethodCore(req, method);
        applyCountryRegion(req, method);

        ShippingMethod saved = methodRepository.save(method);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/methods/{id}")
    @Operation(summary = "Update shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> updateMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody ShippingMethodRequest req
    ) {
        Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth);

        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        ShippingMethod method = methodRepository
                .findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

        applyRequestToMethodCore(req, method);
        applyCountryRegion(req, method);

        ShippingMethod saved = methodRepository.save(method);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/methods/{id}")
    @Operation(summary = "Disable shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> deleteMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth);

        ShippingMethod method = methodRepository
                .findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

        method.setEnabled(false);
        methodRepository.save(method);

        return ResponseEntity.ok(Map.of("message", "Disabled"));
    }

    @GetMapping("/methods/{id}")
    @Operation(summary = "Get shipping method by id (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> getMethodById(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth);

        ShippingMethod method = methodRepository
                .findByIdAndOwnerProject_Id(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

        return ResponseEntity.ok(toResponse(method));
    }

    @GetMapping("/methods")
    @Operation(summary = "List shipping methods for ownerProject (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> listMethodsForOwner(@RequestHeader("Authorization") String auth) {
        Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth);

        List<ShippingMethodResponse> result = methodRepository.findByOwnerProject_Id(ownerProjectId)
                .stream().map(this::toResponse).toList();

        return ResponseEntity.ok(result);
    }

    /* ====================================================
       Public enabled methods list for an app/store
       ==================================================== */

    @GetMapping("/methods/public")
    @Operation(summary = "List enabled shipping methods for app")
    public ResponseEntity<?> listPublicMethods(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam Long ownerProjectId
    ) {
        // Option A: allow guests → no role check at all
        // Option B: require auth → keep this gate
        if (auth != null && !auth.isBlank()) {
            if (!hasRole(auth, "USER", "BUSINESS", "OWNER", "SUPER_ADMIN")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized role"));
            }

            Long tokenTenant = safeTenantFromToken(auth);
            if (tokenTenant != null && !tokenTenant.equals(ownerProjectId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Tenant mismatch"));
            }
        }

        List<ShippingMethodResponse> result = methodRepository
                .findByOwnerProject_IdAndEnabledTrue(ownerProjectId)
                .stream().map(this::toResponse).toList();

        return ResponseEntity.ok(result);
    }

    /* ===================== error handlers ===================== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        // If you prefer 404 for "not found", throw NoSuchElementException and map it separately.
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of("error", "Server error"));
    }
}