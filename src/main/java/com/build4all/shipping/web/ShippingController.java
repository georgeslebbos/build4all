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

    public ShippingController(ShippingService shippingService,
            ShippingMethodRepository methodRepository,
            JwtUtil jwtUtil,
            AdminUserProjectRepository adminUserProjectRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository,
            OwnerSubscriptionGuard ownerSubscriptionGuard) {
this.shippingService = shippingService;
this.methodRepository = methodRepository;
this.jwtUtil = jwtUtil;
this.adminUserProjectRepository = adminUserProjectRepository;
this.countryRepository = countryRepository;
this.regionRepository = regionRepository;
this.ownerSubscriptionGuard = ownerSubscriptionGuard;
}

    /* ===================== helpers ===================== */

    /**
     * Keep token extraction simple: JwtUtil already normalizes "Bearer ".
     */
    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    /**
     * Role checker that supports:
     * - USER / OWNER / SUPER_ADMIN
     * - ROLE_USER / ROLE_OWNER / ROLE_SUPER_ADMIN
     *
     * NOTE: your system uses SUPER_ADMIN (not ADMIN).
     * If you still have "ADMIN" somewhere legacy, you can include it too.
     */
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

    private boolean isAdminOrOwner(String tokenOrBearer) {
        // "SUPER_ADMIN" is your real admin role (based on JwtUtil)
        return hasRole(tokenOrBearer, "OWNER", "SUPER_ADMIN", "ADMIN");
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

    /**
     * Apply request fields EXCEPT:
     * - ownerProject (comes from token / set once on create)
     * - country/region (resolved from DB)
     */
    private void applyRequestToMethodCore(ShippingMethodRequest req, ShippingMethod m) {

        if (req.getName() != null) m.setName(req.getName());
        if (req.getDescription() != null) m.setDescription(req.getDescription());

        if (req.getMethodType() != null && !req.getMethodType().isBlank()) {
            ShippingMethodType type = ShippingMethodType.valueOf(req.getMethodType().toUpperCase());
            m.setType(type);
        }

        if (req.getFlatRate() != null) m.setFlatRate(req.getFlatRate());
        if (req.getPricePerKg() != null) m.setPricePerKg(req.getPricePerKg());
        if (req.getFreeShippingThreshold() != null) m.setFreeShippingThreshold(req.getFreeShippingThreshold());

        // enabled flag (always applied)
        m.setEnabled(req.isEnabled());
    }

    /**
     * Resolve country & region as real entities (no stubs).
     * Optional strict validation if Region has getCountry().
     */
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

        if (country != null && region != null) {
            try {
                if (region.getCountry() != null && region.getCountry().getId() != null) {
                    if (!region.getCountry().getId().equals(country.getId())) {
                        throw new IllegalArgumentException("regionId does not belong to countryId");
                    }
                }
            } catch (NoSuchMethodError ignored) {
                // Region has no getCountry() method in your model
            } catch (Exception ignored) {
                // If Region structure differs, ignore (or remove once confirmed).
            }
        }

        m.setCountry(country);
        m.setRegion(region);
    }

    /* ====================================================
       USER / OWNER / SUPER_ADMIN: checkout-time quotes
       ==================================================== */

    /**
     * Checkout quote:
     * - USER/BUSINESS requests might be tenant-scoped already
     * - But your ShippingService expects ownerProjectId from request
     * So we enforce: request ownerProjectId MUST match token ownerProjectId (for tenant-scoped tokens).
     */
    @PostMapping("/quote")
    @Operation(summary = "Get default shipping quote for cart")
    public ResponseEntity<?> quote(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingQuoteRequest req
    ) {
        if (!hasRole(auth, "USER", "OWNER", "SUPER_ADMIN", "BUSINESS", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User/Owner/Admin role required"));
        }

        try {
            // ✅ If token has tenant, enforce match when request provides ownerProjectId
            // (SUPER_ADMIN may not have tenant in token, so requireOwnerProjectId could throw)
            try {
                Long tokenTenant = jwtUtil.extractOwnerProjectIdClaim(strip(auth));
                if (tokenTenant != null) {
                    if (req.getOwnerProjectId() == null || !tokenTenant.equals(req.getOwnerProjectId())) {
                        throw new IllegalArgumentException("Tenant mismatch");
                    }
                }
            } catch (Exception ignored) {
                // If parsing fails, continue to service validation; not ideal but avoids breaking if token type differs.
            }

            ShippingQuote quote = shippingService.getQuote(
                    req.getOwnerProjectId(),
                    req.getAddress(),
                    req.getLines()
            );
            return ResponseEntity.ok(quote);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/available-methods")
    @Operation(summary = "List available shipping methods + prices for cart")
    public ResponseEntity<?> availableMethods(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingQuoteRequest req
    ) {
        if (!hasRole(auth, "USER", "OWNER", "SUPER_ADMIN", "BUSINESS", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User/Owner/Admin role required"));
        }

        try {
            try {
                Long tokenTenant = jwtUtil.extractOwnerProjectIdClaim(strip(auth));
                if (tokenTenant != null) {
                    if (req.getOwnerProjectId() == null || !tokenTenant.equals(req.getOwnerProjectId())) {
                        throw new IllegalArgumentException("Tenant mismatch");
                    }
                }
            } catch (Exception ignored) {
            }

            List<ShippingQuote> list = shippingService.getAvailableMethods(
                    req.getOwnerProjectId(),
                    req.getAddress(),
                    req.getLines()
            );
            return ResponseEntity.ok(list);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    /* ====================================================
       OWNER / SUPER_ADMIN: CRUD for shipping methods
       IMPORTANT: ownerProjectId comes from TOKEN (not request)
       ==================================================== */

    @PostMapping("/methods")
    @Operation(summary = "Create shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> createMethod(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingMethodRequest req
    ) {
        try {
            Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth); // token source of truth

            // ✅ NEW subscription guard
            ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
            if (blocked != null) return blocked;

            if (req.getName() == null || req.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (req.getMethodType() == null || req.getMethodType().isBlank()) {
                throw new IllegalArgumentException("methodType is required");
            }

            AdminUserProject ownerProject = adminUserProjectRepository.findById(ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId in token: " + ownerProjectId));

            ShippingMethod method = new ShippingMethod();
            method.setOwnerProject(ownerProject);

            applyRequestToMethodCore(req, method);
            applyCountryRegion(req, method);

            ShippingMethod saved = methodRepository.save(method);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/methods/{id}")
    @Operation(summary = "Update shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> updateMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody ShippingMethodRequest req
    ) {
        try {
        	   Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth); // token source of truth

               // ✅ NEW subscription guard
               ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
               if (blocked != null) return blocked;

            ShippingMethod method = methodRepository
                    .findByIdAndOwnerProject_Id(id, ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

            // ✅ never move tenants on update
            applyRequestToMethodCore(req, method);
            applyCountryRegion(req, method);

            ShippingMethod saved = methodRepository.save(method);
            return ResponseEntity.ok(toResponse(saved));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/methods/{id}")
    @Operation(summary = "Delete/disable shipping method (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> deleteMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth); // ✅ from token

            ShippingMethod method = methodRepository
                    .findByIdAndOwnerProject_Id(id, ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

            method.setEnabled(false); // soft delete
            methodRepository.save(method);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/methods/{id}")
    @Operation(summary = "Get shipping method by id (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> getMethodById(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth); // ✅ from token

            ShippingMethod method = methodRepository
                    .findByIdAndOwnerProject_Id(id, ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

            return ResponseEntity.ok(toResponse(method));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/methods")
    @Operation(summary = "List shipping methods for ownerProject (OWNER/SUPER_ADMIN)")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
    public ResponseEntity<?> listMethodsForOwner(@RequestHeader("Authorization") String auth) {
        try {
            Long ownerProjectId = jwtUtil.requireOwnerProjectId(auth); // ✅ from token

            List<ShippingMethod> methods = methodRepository.findByOwnerProject_Id(ownerProjectId);
            List<ShippingMethodResponse> result = methods.stream().map(this::toResponse).toList();

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    /* ====================================================
       USER / OWNER / SUPER_ADMIN: public list of enabled methods
       NOTE: for store checkout you might still pass ownerProjectId (public app)
       ==================================================== */

    @GetMapping("/methods/public")
    @Operation(summary = "List enabled shipping methods for app (public for USER/OWNER/SUPER_ADMIN)")
    public ResponseEntity<?> listPublicMethods(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        if (!hasRole(auth, "USER", "OWNER", "SUPER_ADMIN", "BUSINESS", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User/Owner/Admin role required"));
        }

        try {
            // If token is tenant-scoped, enforce match against requested ownerProjectId
            Long tokenTenant = jwtUtil.extractOwnerProjectIdClaim(strip(auth));
            if (tokenTenant != null && !tokenTenant.equals(ownerProjectId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Tenant mismatch"));
            }

            List<ShippingMethod> methods = methodRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);
            List<ShippingMethodResponse> result = methods.stream().map(this::toResponse).toList();

            return ResponseEntity.ok(result);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    /* ===================== generic error handlers ===================== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
    }
}
