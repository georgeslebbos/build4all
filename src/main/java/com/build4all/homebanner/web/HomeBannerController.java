package com.build4all.homebanner.web;

import com.build4all.homebanner.dto.HomeBannerRequest;
import com.build4all.homebanner.dto.HomeBannerResponse;
import com.build4all.homebanner.service.HomeBannerService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home-banners")
public class HomeBannerController {

    private final HomeBannerService bannerService;
    private final JwtUtil jwtUtil;

    public HomeBannerController(HomeBannerService bannerService, JwtUtil jwtUtil) {
        this.bannerService = bannerService;
        this.jwtUtil = jwtUtil;
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

    @PostMapping
    @Operation(summary = "Create home banner")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody HomeBannerRequest request
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            HomeBannerResponse saved = bannerService.create(ownerId, request);
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

    @PutMapping("/{id}")
    @Operation(summary = "Update home banner")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody HomeBannerRequest request
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            HomeBannerResponse updated = bannerService.update(ownerId, id, request);
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
