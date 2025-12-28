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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final JwtUtil jwtUtil;

    public CouponController(CouponService couponService, JwtUtil jwtUtil) {
        this.couponService = couponService;
        this.jwtUtil = jwtUtil;
    }

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    /**
     * Supports both OWNER and ROLE_OWNER (Spring Security often uses ROLE_*).
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

    /* ============================
       CREATE
       ============================ */

    @PostMapping
    @Operation(summary = "Create coupon (OWNER only)")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody CouponRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required"));
        }

        try {
            CouponResponse created = couponService.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ============================
       UPDATE
       ============================ */

    @PutMapping("/{id}")
    @Operation(summary = "Update coupon (OWNER only)")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam Long ownerProjectId,
            @RequestBody CouponRequest req
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required"));
        }

        try {
            Coupon updated = couponService.update(ownerProjectId, id, req);
            return ResponseEntity.ok(couponService.toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ============================
       DELETE
       ============================ */

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon (OWNER only)")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required"));
        }

        try {
            couponService.delete(ownerProjectId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ============================
       GET / LIST
       ============================ */

    @GetMapping("/{id}")
    @Operation(summary = "Get coupon by id (OWNER only)")
    public ResponseEntity<?> get(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required"));
        }

        try {
            Coupon c = couponService.get(ownerProjectId, id);
            return ResponseEntity.ok(couponService.toResponse(c));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "List coupons for one app (OWNER only)")
    public ResponseEntity<?> list(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required"));
        }

        List<CouponResponse> list = couponService.listByOwnerProject(ownerProjectId)
                .stream()
                .map(couponService::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    /* ============================
       VALIDATE
       ============================ */

    @GetMapping("/validate")
    @Operation(summary = "Validate coupon code for given ownerProject and amount")
    public ResponseEntity<?> validate(
            @RequestParam Long ownerProjectId,
            @RequestParam String code,
            @RequestParam BigDecimal itemsSubtotal
    ) {
        try {
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
