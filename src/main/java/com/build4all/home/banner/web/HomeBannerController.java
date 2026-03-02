package com.build4all.home.banner.web;

import com.build4all.home.banner.dto.HomeBannerRequest;
import com.build4all.home.banner.dto.HomeBannerResponse;
import com.build4all.home.banner.service.HomeBannerService;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home-banners")
public class HomeBannerController {

    private final HomeBannerService bannerService;
    private final JwtUtil jwtUtil;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;

    public HomeBannerController(
            HomeBannerService bannerService,
            JwtUtil jwtUtil,
            OwnerSubscriptionGuard ownerSubscriptionGuard
    ) {
        this.bannerService = bannerService;
        this.jwtUtil = jwtUtil;
        this.ownerSubscriptionGuard = ownerSubscriptionGuard;
    }

    /* ------------------------ helpers ------------------------ */

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private Long requireTenantFromToken(String authHeader) {
        // JwtUtil already normalizes bearer in requireOwnerProjectId
        // and validates token.
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    private Long requireOwnerIdFromToken(String authHeader) {
        String token = strip(authHeader);
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        return jwtUtil.extractId(token); // adminId for OWNER tokens (per your JwtUtil)
    }

    /* ------------------------ USER / OWNER: list active banners (slider) ------------------------ */

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    @Operation(summary = "List active home banners (slider) for current tenant (ownerProjectId from token)")
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {
        try {
            Long ownerProjectId = requireTenantFromToken(auth);
            List<HomeBannerResponse> result = bannerService.listActivePublic(ownerProjectId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // token / tenant missing / invalid
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: list all banners of his app (admin view) ------------------------ */

    @GetMapping("/app")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Admin (OWNER): list all banners for current tenant (ownerProjectId from token)")
    public ResponseEntity<?> listForApp(@RequestHeader("Authorization") String auth) {
        try {
            Long ownerId = requireOwnerIdFromToken(auth);
            Long ownerProjectId = requireTenantFromToken(auth);

            List<HomeBannerResponse> result =
                    bannerService.listByOwnerProjectForAdmin(ownerProjectId, ownerId);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: create ------------------------ */

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Create home banner with image (tenant from token, flat form-data)")
    public ResponseEntity<?> createWithImage(
            @RequestHeader("Authorization") String auth,

            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt,

            @RequestParam(value = "image") MultipartFile image
    ) {
        try {
            Long ownerId = requireOwnerIdFromToken(auth);
            Long tokenOwnerProjectId = requireTenantFromToken(auth);

            // ✅ subscription guard per tenant
            ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(tokenOwnerProjectId);
            if (blocked != null) return blocked;

            HomeBannerRequest req = new HomeBannerRequest();

            // ✅ trust token tenant only
            req.setOwnerProjectId(tokenOwnerProjectId);

            req.setTitle(title);
            req.setSubtitle(subtitle);
            req.setTargetType(targetType);
            req.setTargetId(targetId);
            req.setTargetUrl(targetUrl);
            req.setSortOrder(sortOrder);
            req.setActive(active);

            if (startAt != null && !startAt.isBlank()) req.setStartAt(LocalDateTime.parse(startAt));
            if (endAt != null && !endAt.isBlank()) req.setEndAt(LocalDateTime.parse(endAt));

            HomeBannerResponse saved = bannerService.createWithImage(ownerId, req, image);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: update ------------------------ */

    @PutMapping(value = "/{id}/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Update home banner with optional image (tenant from token, flat form-data)")
    public ResponseEntity<?> updateWithImage(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,

            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String subtitle,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(required = false) Integer sortOrder,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String startAt,
            @RequestParam(required = false) String endAt,

            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        try {
            Long ownerId = requireOwnerIdFromToken(auth);

            // ✅ tenant extracted from token (no request param)
            Long tokenOwnerProjectId = requireTenantFromToken(auth);

            // ✅ subscription guard per tenant
            ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(tokenOwnerProjectId);
            if (blocked != null) return blocked;

            HomeBannerRequest req = new HomeBannerRequest();
            req.setImageUrl(imageUrl);
            req.setTitle(title);
            req.setSubtitle(subtitle);
            req.setTargetType(targetType);
            req.setTargetId(targetId);
            req.setTargetUrl(targetUrl);
            req.setSortOrder(sortOrder);
            req.setActive(active);

            if (startAt != null && !startAt.isBlank()) req.setStartAt(LocalDateTime.parse(startAt));
            if (endAt != null && !endAt.isBlank()) req.setEndAt(LocalDateTime.parse(endAt));

            HomeBannerResponse updated = bannerService.updateWithImage(ownerId, id, req, image);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: delete ------------------------ */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Delete home banner")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        try {
            Long ownerId = requireOwnerIdFromToken(auth);
            bannerService.delete(ownerId, id);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}