package com.build4all.tax.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.security.JwtUtil;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.dto.TaxPreviewRequest;
import com.build4all.tax.dto.TaxRuleRequest;
import com.build4all.tax.dto.TaxRuleResponse;
import com.build4all.tax.service.TaxService;
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

    public TaxController(TaxService taxService, JwtUtil jwtUtil) {
        this.taxService = taxService;
        this.jwtUtil = jwtUtil;
    }

    /* ===================== helpers ===================== */

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    /**
     * Check if current token has one of the given roles.
     * Supports "OWNER", "USER", "ADMIN" and "ROLE_*" variants.
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

    private TaxRule fromRequest(TaxRuleRequest req) {
        TaxRule rule = new TaxRule();

        if (req.getOwnerProjectId() != null) {
            AdminUserProject proj = new AdminUserProject();
            proj.setId(req.getOwnerProjectId());
            rule.setOwnerProject(proj);
        }

        rule.setName(req.getName());
        rule.setRate(req.getRate());
        rule.setAppliesToShipping(req.isAppliesToShipping());
        rule.setEnabled(req.isEnabled());

        if (req.getCountryId() != null) {
            Country c = new Country();
            c.setId(req.getCountryId());
            rule.setCountry(c);
        }

        if (req.getRegionId() != null) {
            Region r = new Region();
            r.setId(req.getRegionId());
            rule.setRegion(r);
        }

        return rule;
    }

    private TaxRuleResponse toResponse(TaxRule rule) {
        TaxRuleResponse r = new TaxRuleResponse();
        r.setId(rule.getId());
        r.setOwnerProjectId(
                rule.getOwnerProject() != null ? rule.getOwnerProject().getId() : null
        );
        r.setName(rule.getName());
        r.setRate(rule.getRate());
        r.setAppliesToShipping(rule.isAppliesToShipping());
        r.setEnabled(rule.isEnabled());
        r.setCountryId(
                rule.getCountry() != null ? rule.getCountry().getId() : null
        );
        r.setRegionId(
                rule.getRegion() != null ? rule.getRegion().getId() : null
        );
        return r;
    }

    /* ====================================================
       ADMIN / OWNER: CRUD on Tax Rules
       ==================================================== */

    @PostMapping("/rules")
    @Operation(summary = "Create tax rule (OWNER/ADMIN only)")
    public ResponseEntity<?> createRule(
            @RequestHeader("Authorization") String auth,
            @RequestBody TaxRuleRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            TaxRule created = taxService.createRule(fromRequest(req));
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update tax rule (OWNER/ADMIN only)")
    public ResponseEntity<?> updateRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody TaxRuleRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            TaxRule updates = fromRequest(req);
            TaxRule updated = taxService.updateRule(id, updates);
            return ResponseEntity.ok(toResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Delete tax rule (OWNER/ADMIN only)")
    public ResponseEntity<?> deleteRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            taxService.deleteRule(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get tax rule by id (OWNER/ADMIN only)")
    public ResponseEntity<?> getRule(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            TaxRule rule = taxService.getRule(id);
            return ResponseEntity.ok(toResponse(rule));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/rules")
    @Operation(summary = "List tax rules for an app (OWNER/ADMIN only)")
    public ResponseEntity<?> listRules(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner/Admin role required"));
        }

        try {
            List<TaxRule> rules = taxService.listRulesByOwnerProject(ownerProjectId);
            List<TaxRuleResponse> resp = rules.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    /* ====================================================
       USER / OWNER / ADMIN: preview tax calculation
       ==================================================== */

    @PostMapping("/preview")
    @Operation(summary = "Preview item + shipping tax for given cart")
    public ResponseEntity<?> previewTax(
            @RequestHeader("Authorization") String auth,
            @RequestBody TaxPreviewRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "USER", "OWNER", "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User/Owner/Admin role required"));
        }

        try {
            BigDecimal itemTax = taxService.calculateItemTax(
                    req.getOwnerProjectId(),
                    req.getAddress(),
                    req.getLines()
            );

            BigDecimal shippingTax = taxService.calculateShippingTax(
                    req.getOwnerProjectId(),
                    req.getAddress(),
                    req.getShippingTotal()
            );

            return ResponseEntity.ok(Map.of(
                    "itemsTaxTotal", itemTax,
                    "shippingTaxTotal", shippingTax,
                    "totalTax", itemTax.add(shippingTax)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
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
