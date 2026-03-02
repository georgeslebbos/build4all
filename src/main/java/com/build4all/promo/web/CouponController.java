package com.build4all.promo.web;

import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.service.LicensingService;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.dto.CouponRequest;
import com.build4all.promo.dto.CouponResponse;
import com.build4all.promo.service.CouponService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final JwtUtil jwtUtil;
    private final LicensingService licensingService;

    public CouponController(CouponService couponService, JwtUtil jwtUtil, LicensingService licensingService) {
        this.couponService = couponService;
        this.jwtUtil = jwtUtil;
        this.licensingService = licensingService;
    }

    /* ========================= helpers ========================= */

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private Long tenantFromToken(String authHeader) {
        // ✅ requireOwnerProjectId should validate token + return tenant claim
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    /**
     * Subscription block for write operations.
     * Returns null if allowed; otherwise returns a ResponseEntity to return immediately.
     */
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

    /* ========================= CREATE (OWNER) ========================= */

    @PostMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create coupon (OWNER)")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody CouponRequest req
    ) {
        try {
            Long ownerProjectId = tenantFromToken(auth);

            ResponseEntity<?> blocked = blockIfSubscriptionExceeded(ownerProjectId);
            if (blocked != null) return blocked;

            // ✅ do NOT trust client tenant
            req.setOwnerProjectId(ownerProjectId);

            CouponResponse created = couponService.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    /* ========================= UPDATE (OWNER) ========================= */

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update coupon (OWNER)")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody CouponRequest req
    ) {
        try {
            Long ownerProjectId = tenantFromToken(auth);

            // ✅ ignore req.ownerProjectId completely (tenant comes from token)
            Coupon updated = couponService.update(ownerProjectId, id, req);
            return ResponseEntity.ok(couponService.toResponse(updated));

        } catch (IllegalArgumentException e) {
            // For tenant isolation, treat as not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    /* ========================= DELETE (OWNER) ========================= */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete coupon (OWNER)")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            Long ownerProjectId = tenantFromToken(auth);
            couponService.delete(ownerProjectId, id);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    /* ========================= GET / LIST (OWNER) ========================= */

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get coupon by id (OWNER)")
    public ResponseEntity<?> get(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            Long ownerProjectId = tenantFromToken(auth);
            Coupon c = couponService.get(ownerProjectId, id);
            return ResponseEntity.ok(couponService.toResponse(c));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "List coupons for my app (OWNER) ")
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {
        try {
            Long ownerProjectId = tenantFromToken(auth);

            List<CouponResponse> list = couponService.listByOwnerProject(ownerProjectId)
                    .stream()
                    .map(couponService::toResponse)
                    .toList();

            return ResponseEntity.ok(list);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    /* ========================= VALIDATE (Checkout) ========================= */

    /**
     * ✅ Token required. No ownerProjectId param. No optional fallback.
     *
     * Why:
     * - avoids tenant spoofing
     * - consistent with “extract tenant from token” everywhere
     *
     * If you truly need guest checkout coupons, create a separate endpoint:
     *   GET /api/coupons/validate/guest?ownerProjectId=...&code=...&itemsSubtotal=...
     * with strict public rules (and rate-limit it).
     */
    @GetMapping("/validate")
    @PreAuthorize("hasRole('USER') or hasRole('OWNER') or hasRole('SUPER_ADMIN')") // USER checkout + OWNER testing
    @Operation(summary = "Validate coupon code (tenant from JWT)")
    public ResponseEntity<?> validate(
            @RequestHeader("Authorization") String auth,
            @RequestParam String code,
            @RequestParam BigDecimal itemsSubtotal
    ) {
        try {
            Long ownerProjectId = tenantFromToken(auth);

            Coupon coupon = couponService.validateForOrder(ownerProjectId, code, itemsSubtotal);
            if (coupon == null) {
                return ResponseEntity.ok(Map.of(
                        "valid", false,
                        "error", "Coupon not valid for this amount or date"
                ));
            }

            BigDecimal discount = couponService.computeDiscount(coupon, itemsSubtotal);

            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "code", coupon.getCode(),
                    "discount", discount
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "valid", false,
                    "error", "Server error"
            ));
        }
    }
}