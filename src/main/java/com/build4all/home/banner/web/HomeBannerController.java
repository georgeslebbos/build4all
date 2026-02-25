package com.build4all.home.banner.web;

import com.build4all.home.banner.dto.HomeBannerRequest;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.build4all.home.banner.dto.HomeBannerResponse;
import com.build4all.home.banner.service.HomeBannerService;
import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home-banners")
public class HomeBannerController {

    private final HomeBannerService bannerService;
    private final JwtUtil jwtUtil;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;
    
    public HomeBannerController(HomeBannerService bannerService,
            JwtUtil jwtUtil,
            OwnerSubscriptionGuard ownerSubscriptionGuard) {
this.bannerService = bannerService;
this.jwtUtil = jwtUtil;
this.ownerSubscriptionGuard = ownerSubscriptionGuard;
}
    /* ------------------------ helpers (same pattern as ProductController) ------------------------ */

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    /* ------------------------ USER / OWNER: list active banners (slider) ------------------------ */

    @GetMapping
    @Operation(summary = "List active home banners (slider) for an app (ownerProject)")
    public ResponseEntity<?> list(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        try {
            List<HomeBannerResponse> result = bannerService.listActivePublic(ownerProjectId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: list all banners of his app (admin view) ------------------------ */

    @GetMapping("/app")
    @Operation(summary = "Admin (OWNER): list all banners for an app (ownerProject)")
    public ResponseEntity<?> listForApp(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            List<HomeBannerResponse> result =
                    bannerService.listByOwnerProjectForAdmin(ownerProjectId, ownerId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: create ------------------------ */

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create home banner with image (flat form-data)")
    public ResponseEntity<?> createWithImage(
            @RequestHeader("Authorization") String auth,

            @RequestParam Long ownerProjectId,
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
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);

            // ✅ Stronger: ownerProjectId must come from token (or at least match token claim)
            Long tokenOwnerProjectId = jwtUtil.requireOwnerProjectId(auth);
            if (!tokenOwnerProjectId.equals(ownerProjectId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tenant mismatch"));
            }

            // ✅ NEW subscription guard
            ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(tokenOwnerProjectId);
            if (blocked != null) return blocked;

            HomeBannerRequest req = new HomeBannerRequest();

            // ✅ trust token tenant, not request param
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: update ------------------------ */

    @PutMapping(value = "/{id}/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update home banner with optional image (flat form-data)")
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
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Owner role required."));
        }

        try {
        	Long ownerId = jwtUtil.extractId(token);

            // ✅ Stronger: ownerProjectId must come from token (or at least match token claim)
            Long tokenOwnerProjectId = jwtUtil.requireOwnerProjectId(auth);
          
            // ✅ NEW subscription guard
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ OWNER: delete ------------------------ */

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete home banner")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            bannerService.delete(ownerId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
