package com.build4all.promo.web;

import com.build4all.promo.domain.Coupon;
import com.build4all.promo.dto.CouponRequest;
import com.build4all.promo.dto.CouponResponse;
import com.build4all.promo.service.CouponService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final JwtUtil jwtUtil;

    public CouponController(CouponService couponService, JwtUtil jwtUtil) {
        this.couponService = couponService;
        this.jwtUtil = jwtUtil;
    }

    private boolean hasRole(String tokenOrBearer, String... roles) {
        String role = jwtUtil.extractRole(tokenOrBearer);
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

    private Long tenantFromToken(String authHeader) {
        // ✅ your JwtUtil already normalizes/validates in requireOwnerProjectId
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    /* ==========================
       CREATE (OWNER)
       ========================== */

    @PostMapping
    @Operation(summary = "Create coupon (OWNER only)")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody CouponRequest req
    ) {
        if (!hasRole(auth, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required"));
        }

        try {
            Long ownerProjectId = tenantFromToken(auth);

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

    /* ==========================
       UPDATE (OWNER)
       ========================== */

    @PutMapping("/{id}")
    @Operation(summary = "Update coupon (OWNER only)")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody CouponRequest req
    ) {
        if (!hasRole(auth, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required"));
        }

        try {
            Long ownerProjectId = tenantFromToken(auth);

            // ✅ ignore req.ownerProjectId completely
            Coupon updated = couponService.update(ownerProjectId, id, req);
            return ResponseEntity.ok(couponService.toResponse(updated));
        } catch (IllegalArgumentException e) {
            // use 404-ish message to avoid leaking tenant/coupon existence details
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }

    /* ==========================
       DELETE (OWNER)
       ========================== */

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon (OWNER only)")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        if (!hasRole(auth, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required"));
        }

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

    /* ==========================
       GET / LIST (OWNER)
       ========================== */

    @GetMapping("/{id}")
    @Operation(summary = "Get coupon by id (OWNER only)")
    public ResponseEntity<?> get(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        if (!hasRole(auth, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required"));
        }

        try {
            Long ownerProjectId = tenantFromToken(auth);
            Coupon c = couponService.get(ownerProjectId, id);
            return ResponseEntity.ok(couponService.toResponse(c));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "List coupons for my app (OWNER only)")
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {
        if (!hasRole(auth, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required"));
        }

        Long ownerProjectId = tenantFromToken(auth);

        List<CouponResponse> list = couponService.listByOwnerProject(ownerProjectId)
                .stream()
                .map(couponService::toResponse)
                .toList();

        return ResponseEntity.ok(list);
    }

    /* ==========================
       VALIDATE (Checkout)
       ========================== */

    @GetMapping("/validate")
    @Operation(summary = "Validate coupon code (token tenant preferred)")
    public ResponseEntity<?> validate(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Long ownerProjectId,
            @RequestParam String code,
            @RequestParam BigDecimal itemsSubtotal
    ) {
        try {
            // ✅ If authenticated, tenant always from token
            if (auth != null && !auth.isBlank()) {
                ownerProjectId = jwtUtil.requireOwnerProjectId(auth);
            }

            // ✅ Guest fallback (only if you really allow guests)
            if (ownerProjectId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "valid", false,
                        "error", "ownerProjectId is required (or provide Authorization)"
                ));
            }

            Coupon coupon = couponService.validateForOrder(ownerProjectId, code, itemsSubtotal);
            if (coupon == null) {
                return ResponseEntity.ok(Map.of(
                        "valid", false,
                        "error", "Coupon not valid for this amount or date"
                ));
            }

            var discount = couponService.computeDiscount(coupon, itemsSubtotal);
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
        }
    }
}
