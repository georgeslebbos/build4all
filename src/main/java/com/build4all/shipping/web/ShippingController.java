package com.build4all.shipping.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

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

    public ShippingController(ShippingService shippingService,
                              ShippingMethodRepository methodRepository,
                              JwtUtil jwtUtil) {
        this.shippingService = shippingService;
        this.methodRepository = methodRepository;
        this.jwtUtil = jwtUtil;
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

    private void applyRequestToMethod(ShippingMethodRequest req, ShippingMethod m) {

        if (req.getOwnerProjectId() != null) {
            AdminUserProject proj = new AdminUserProject();
            proj.setId(req.getOwnerProjectId());
            m.setOwnerProject(proj);
        }

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

        // enabled flag
        m.setEnabled(req.isEnabled());

        // country / region as "stub" entities by ID (no repository lookup here)
        if (req.getCountryId() != null) {
            Country c = new Country();
            c.setId(req.getCountryId());
            m.setCountry(c);
        }
        if (req.getRegionId() != null) {
            Region r = new Region();
            r.setId(req.getRegionId());
            m.setRegion(r);
        }
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
       /* String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }*/

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

            ShippingMethod method = new ShippingMethod();
            applyRequestToMethod(req, method);

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
    public ResponseEntity<?> updateMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody ShippingMethodRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            ShippingMethod method = methodRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found"));

            applyRequestToMethod(req, method);

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
    public ResponseEntity<?> deleteMethod(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            ShippingMethod method = methodRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found"));

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
    public ResponseEntity<?> getMethodById(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            ShippingMethod method = methodRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Shipping method not found"));
            return ResponseEntity.ok(toResponse(method));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
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
