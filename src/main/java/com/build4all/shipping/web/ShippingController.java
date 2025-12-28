package com.build4all.shipping.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.RegionRepository;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/shipping")
@Tag(name = "Shipping")
@SecurityRequirement(name = "bearerAuth")
public class ShippingController {

    private final ShippingService shippingService;
    private final ShippingMethodRepository methodRepository;
    private final JwtUtil jwtUtil;

    // ✅ NEW: Real lookups to avoid "stub entities" and enforce tenant isolation
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;

    public ShippingController(ShippingService shippingService,
                              ShippingMethodRepository methodRepository,
                              JwtUtil jwtUtil,
                              AdminUserProjectRepository adminUserProjectRepository,
                              CountryRepository countryRepository,
                              RegionRepository regionRepository) {
        this.shippingService = shippingService;
        this.methodRepository = methodRepository;
        this.jwtUtil = jwtUtil;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.countryRepository = countryRepository;
        this.regionRepository = regionRepository;
    }

    /* ===================== helpers ===================== */

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    /**
     * Check if current token has one of the given roles.
     * Supports both "OWNER", "USER", "ADMIN" and "ROLE_*" variants.
     */
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

    /* ===================== mapping helpers (methods) ===================== */

    private ShippingMethodResponse toResponse(ShippingMethod m) {
        ShippingMethodResponse r = new ShippingMethodResponse();
        r.setId(m.getId());
        r.setOwnerProjectId(
                m.getOwnerProject() != null ? m.getOwnerProject().getId() : null
        );
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
     * ✅ NEW:
     * Apply request fields EXCEPT ownerProject/country/region.
     *
     * Why?
     * - ownerProject must be set ONLY once during creation (otherwise you can "move" a method between tenants)
     * - country/region must be resolved from DB (not stubs) to avoid FK problems and to validate IDs
     */
    private void applyRequestToMethodCore(ShippingMethodRequest req, ShippingMethod m) {

        if (req.getName() != null) m.setName(req.getName());
        if (req.getDescription() != null) m.setDescription(req.getDescription());

        if (req.getMethodType() != null && !req.getMethodType().isBlank()) {
            ShippingMethodType type = ShippingMethodType.valueOf(
                    req.getMethodType().toUpperCase()
            );
            m.setType(type);
        }

        if (req.getFlatRate() != null) {
            m.setFlatRate(req.getFlatRate());
        }
        if (req.getPricePerKg() != null) {
            m.setPricePerKg(req.getPricePerKg());
        }
        if (req.getFreeShippingThreshold() != null) {
            m.setFreeShippingThreshold(req.getFreeShippingThreshold());
        }

        // enabled flag (always applied)
        m.setEnabled(req.isEnabled());
    }

    /**
     * ✅ NEW:
     * Resolve country & region as real entities (no stubs).
     *
     * Also adds safety: if Region has a relation to Country, ensure both match.
     * If your Region entity doesn't have getCountry(), this still works without that validation.
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

        // Optional strict validation (only if Region has Country)
        if (country != null && region != null) {
            try {
                if (region.getCountry() != null && region.getCountry().getId() != null) {
                    if (!region.getCountry().getId().equals(country.getId())) {
                        throw new IllegalArgumentException("regionId does not belong to countryId");
                    }
                }
            } catch (Exception ignored) {
                // If Region has no getCountry() in your model, ignore.
                // You can remove this try/catch once you confirm Region structure.
            }
        }

        m.setCountry(country);
        m.setRegion(region);
    }

    /* ====================================================
       USER / OWNER / ADMIN: checkout-time quotes
       ==================================================== */

    @PostMapping("/quote")
    @Operation(summary = "Get default shipping quote for cart")
    public ResponseEntity<?> quote(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingQuoteRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "USER", "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User/Owner/Admin role required"));
        }

        try {
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
        String token = strip(auth);
        if (!hasRole(token, "USER", "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User/Owner/Admin role required"));
        }

        try {
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
       OWNER / ADMIN: CRUD for shipping methods
       ==================================================== */

    @PostMapping("/methods")
    @Operation(summary = "Create shipping method (OWNER/ADMIN)")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> createMethod(
            @RequestHeader("Authorization") String auth,
            @RequestBody ShippingMethodRequest req
    ) {
        /*
         * NOTE:
         * We rely on @PreAuthorize for authorization.
         * If you want to support ADMIN too, change to:
         * @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN')")
         * (and align role names with your system)
         */

        try {
            if (req.getOwnerProjectId() == null) {
                throw new IllegalArgumentException("ownerProjectId is required");
            }
            if (req.getName() == null || req.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (req.getMethodType() == null || req.getMethodType().isBlank()) {
                throw new IllegalArgumentException("methodType is required");
            }

            // ✅ REAL lookup: avoids setting a stub AdminUserProject(id) which can break validation and FK constraints
            AdminUserProject ownerProject = adminUserProjectRepository.findById(req.getOwnerProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId: " + req.getOwnerProjectId()));

            ShippingMethod method = new ShippingMethod();

            // ✅ ownerProject set ONLY at create time (prevents tenant reassignment later)
            method.setOwnerProject(ownerProject);

            // ✅ apply fields (no stubs)
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
    @Operation(summary = "Update shipping method (OWNER/ADMIN)")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> updateMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody ShippingMethodRequest req
    ) {
        /*
         * ✅ IMPORTANT:
         * We scope the update by ownerProjectId to prevent cross-tenant access:
         * - someone cannot update shipping method #10 that belongs to another tenant
         *   just by guessing the ID.
         */
        try {
            if (req.getOwnerProjectId() == null) {
                throw new IllegalArgumentException("ownerProjectId is required (for scoping update)");
            }

            // ✅ secure lookup: must belong to the same ownerProject
            ShippingMethod method = methodRepository
                    .findByIdAndOwnerProject_Id(id, req.getOwnerProjectId())
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

            // ✅ DO NOT change ownerProject on update (ignore req.ownerProjectId as a setter)
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
    @Operation(summary = "Delete/disable shipping method (OWNER/ADMIN)")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> deleteMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam Long ownerProjectId
    ) {
        /*
         * ✅ IMPORTANT:
         * Delete is also scoped by ownerProjectId.
         */
        try {
            ShippingMethod method = methodRepository
                    .findByIdAndOwnerProject_Id(id, ownerProjectId)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found for this ownerProject"));

            // Soft delete: disable instead of physical delete
            method.setEnabled(false);
            methodRepository.save(method);

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/methods/{id}")
    @Operation(summary = "Get shipping method by id (OWNER/ADMIN)")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getMethodById(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam Long ownerProjectId
    ) {
        /*
         * ✅ IMPORTANT:
         * Get-by-id is also scoped by ownerProjectId.
         */
        try {
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
    @Operation(summary = "List shipping methods for ownerProject (OWNER/ADMIN)")
    public ResponseEntity<?> listMethodsForOwner(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        List<ShippingMethod> methods = methodRepository.findByOwnerProject_Id(ownerProjectId);
        List<ShippingMethodResponse> result = methods.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /* ====================================================
       USER / OWNER / ADMIN: public list of enabled methods
       ==================================================== */

    @GetMapping("/methods/public")
    @Operation(summary = "List enabled shipping methods for app (public for USER/OWNER/ADMIN)")
    public ResponseEntity<?> listPublicMethods(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "USER", "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User/Owner/Admin role required"));
        }

        List<ShippingMethod> methods = methodRepository.findByOwnerProject_IdAndEnabledTrue(ownerProjectId);
        List<ShippingMethodResponse> result = methods.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
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
