package com.build4all.home.sections.web;

import com.build4all.home.sections.dto.HomePageResponse;
import com.build4all.home.sections.dto.HomeSectionRequest;
import com.build4all.home.sections.dto.SectionAddProductRequest;
import com.build4all.home.sections.service.HomePageService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HomePageController
 *
 * Purpose:
 * - Exposes REST endpoints to serve the Home page content to the frontend:
 *     1) Public home endpoint: returns "top banners + sections" in one response
 *     2) Owner admin endpoints: allow OWNER to manage sections and assign products
 *
 * Why this controller exists:
 * - The home screen needs multiple blocks of content (banners, sections, products).
 * - Instead of the frontend calling many endpoints, we expose one aggregated endpoint:
 *   GET /api/home?ownerProjectId=...
 *
 * Multi-tenant scope:
 * - ownerProjectId represents the tenant/app scope (AdminUserProject.aup_id).
 * - All "read public home" operations must filter by this tenant id to avoid cross-tenant leakage.
 *
 * Security / Roles:
 * - GET /api/home : USER or OWNER can read.
 * - Section management endpoints: OWNER only.
 * - Ownership validation (the OWNER really owns that AUP/section) is enforced in the service layer
 *   (requireOwnedProject / requireOwnedSection).
 *
 * Notes:
 * - This controller uses the same helper style you used in HomeBannerController:
 *   strip Bearer token, check role using JwtUtil.
 * - Errors are returned as JSON { "error": "..." } or { "message": "..." }.
 */
@RestController
@RequestMapping("/api/home")
public class HomePageController {

    private final HomePageService homePageService;
    private final JwtUtil jwtUtil;

    public HomePageController(HomePageService homePageService, JwtUtil jwtUtil) {
        this.homePageService = homePageService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Remove "Bearer " prefix from Authorization header and return raw JWT.
     * (Same pattern used in other controllers in your project.)
     */
    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    /**
     * Simple role check using role claim from JWT (JwtUtil.extractRole()).
     * Accepts a variable list of roles and matches case-insensitively.
     */
    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    // ---------------- PUBLIC (USER/OWNER) ----------------

    /**
     * Public home endpoint (consumed by Flutter/web client).
     *
     * Endpoint:
     * - GET /api/home?ownerProjectId={AUP_ID}
     *
     * Access:
     * - USER or OWNER token required (controller role check).
     *
     * Returns:
     * - HomePageResponse:
     *     {
     *       banners: [ ... ],
     *       sections: [
     *         { code, title, layout, sortOrder, products:[...] }
     *       ]
     *     }
     *
     * The backend will:
     * - load active banners for that AUP (time-window + active filter)
     * - load active sections and their product links (ordered)
     * - load product summary data for each linked product
     */
    @GetMapping
    @Operation(summary = "Get home page: top banners + sections (products)")
    public ResponseEntity<?> getHome(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);

        // USER and OWNER can see the home content
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        try {
            HomePageResponse result = homePageService.getPublicHome(ownerProjectId);
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // Used for validation errors (e.g. bad ownerProjectId)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            // Unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------- OWNER ADMIN ----------------

    /**
     * Create a new home section (OWNER only).
     *
     * Endpoint:
     * - POST /api/home/sections
     *
     * Access:
     * - OWNER role required.
     *
     * Body:
     * - HomeSectionRequest:
     *   { ownerProjectId, code, title, layout, sortOrder, active }
     *
     * Important:
     * - Ownership validation is in the service:
     *   the OWNER must own the given ownerProjectId (AUP).
     */
    @PostMapping("/sections")
    @Operation(summary = "OWNER: create section")
    public ResponseEntity<?> createSection(
            @RequestHeader("Authorization") String auth,
            @RequestBody HomeSectionRequest req
    ) {
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            return ResponseEntity.status(HttpStatus.CREATED).body(homePageService.createSection(ownerId, req));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a section (OWNER only).
     *
     * Endpoint:
     * - PUT /api/home/sections/{sectionId}
     *
     * Access:
     * - OWNER role required.
     *
     * Body:
     * - HomeSectionRequest (fields can be partial):
     *   { title?, layout?, sortOrder?, active? }
     *
     * Ownership:
     * - The OWNER must own the section through its parent AUP.
     * - Verified in HomePageService.requireOwnedSection(...)
     */
    @PutMapping("/sections/{sectionId}")
    @Operation(summary = "OWNER: update section")
    public ResponseEntity<?> updateSection(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long sectionId,
            @RequestBody HomeSectionRequest req
    ) {
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            return ResponseEntity.ok(homePageService.updateSection(ownerId, sectionId, req));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a section (OWNER only).
     *
     * Endpoint:
     * - DELETE /api/home/sections/{sectionId}
     *
     * Access:
     * - OWNER role required.
     *
     * Behavior:
     * - If DB FK is configured with ON DELETE CASCADE, related home_section_products are deleted automatically.
     */
    @DeleteMapping("/sections/{sectionId}")
    @Operation(summary = "OWNER: delete section")
    public ResponseEntity<?> deleteSection(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long sectionId
    ) {
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            homePageService.deleteSection(ownerId, sectionId);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add a product to a section (OWNER only).
     *
     * Endpoint:
     * - POST /api/home/sections/{sectionCode}/products?ownerProjectId={AUP_ID}
     *
     * Access:
     * - OWNER role required.
     *
     * Params:
     * - ownerProjectId: AUP id (tenant scope)
     * - sectionCode: stable section identifier (e.g., "flash_sale")
     *
     * Body:
     * - SectionAddProductRequest:
     *   { productId, sortOrder?, active? }
     *
     * Behavior:
     * - Validates ownership of the AUP
     * - Finds section by (aup_id + code)
     * - Validates product exists
     * - Prevents duplicates (returns existing link if already linked)
     */
    @PostMapping("/sections/{sectionCode}/products")
    @Operation(summary = "OWNER: add product to section")
    public ResponseEntity<?> addProduct(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
            @PathVariable String sectionCode,
            @RequestBody SectionAddProductRequest req
    ) {
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(homePageService.addProduct(ownerId, ownerProjectId, sectionCode, req));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove a product from a section (OWNER only).
     *
     * Endpoint:
     * - DELETE /api/home/sections/{sectionCode}/products/{productId}?ownerProjectId={AUP_ID}
     *
     * Access:
     * - OWNER role required.
     *
     * Behavior:
     * - Validates ownership of the AUP
     * - Finds section by (aup_id + code)
     * - Deletes the link row from home_section_products
     */
    @DeleteMapping("/sections/{sectionCode}/products/{productId}")
    @Operation(summary = "OWNER: remove product from section")
    public ResponseEntity<?> removeProduct(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
            @PathVariable String sectionCode,
            @PathVariable Long productId
    ) {
        String token = strip(auth);

        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Owner role required."));
        }

        try {
            Long ownerId = jwtUtil.extractId(token);
            homePageService.removeProduct(ownerId, ownerProjectId, sectionCode, productId);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
