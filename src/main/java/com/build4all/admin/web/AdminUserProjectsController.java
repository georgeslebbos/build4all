package com.build4all.admin.web;

import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.service.AdminUserProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin-users/{adminId}")
/**
 * AdminUserProjectsController
 *
 * What this controller is about:
 * - It manages the relationship "Owner/Admin (adminId) -> Project (projectId) -> App (slug)".
 * - In your DB model, this is mainly represented by the AdminUserProject entity/table.
 *
 * Why "App-aware"?
 * - One owner can have multiple apps under the SAME project.
 * - Each app is identified by a unique "slug" within (adminId, projectId).
 *   Example:
 *     (adminId=10, projectId=5, slug="shop")
 *     (adminId=10, projectId=5, slug="booking")
 *
 * Return format:
 * - Most endpoints return AdminAppAssignmentResponse which is a DTO containing app metadata:
 *   project info, app name, slug, status, license, validity, theme, artifact URLs, logo, currency.
 *
 * Note:
 * - This controller does not include explicit JWT checks. Usually, authorization is enforced via
 *   Spring Security filters and method security elsewhere.
 */
public class AdminUserProjectsController {

    // Service layer that contains the core business logic (create/update/delete/fetch)
    private final AdminUserProjectService service;

    public AdminUserProjectsController(AdminUserProjectService service) {
        this.service = service;
    }

    // ---------------------------
    // APP-AWARE ENDPOINTS (NEW)
    // ---------------------------

    /** List all apps (rows) for an owner (adminId). */
    @GetMapping("/apps")
    public ResponseEntity<List<AdminAppAssignmentResponse>> listApps(@PathVariable Long adminId) {
        // Calls service.list(adminId) which reads all AdminUserProject rows for that owner
        // and maps them into AdminAppAssignmentResponse DTOs.
        return ResponseEntity.ok(service.list(adminId));
    }

    /** Create or overwrite a single app under a project (owner+project+slug). */
    @PostMapping("/projects/{projectId}/apps")
    public ResponseEntity<Void> assignApp(@PathVariable Long adminId,
                                          @PathVariable Long projectId,
                                          @RequestBody AdminAppAssignmentRequest req) {

        // Enforce projectId from the URL path to avoid client sending a different projectId in body.
        req.setProjectId(projectId);

        // service.assign(...) handles:
        // - slug generation if missing
        // - slug uniqueness enforcement per (adminId, projectId)
        // - create new row if missing, otherwise update existing row
        // - default licenseId / validity if missing
        service.assign(adminId, req);

        // 204 No Content indicates success with no response payload.
        return ResponseEntity.noContent().build();
    }

    /** Update only license/validity for an existing app (owner+project+slug). */
    @PutMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<Void> updateAppLicense(@PathVariable Long adminId,
                                                 @PathVariable Long projectId,
                                                 @PathVariable String slug,
                                                 @RequestBody AdminAppAssignmentRequest req) {

        // This is intentionally narrower than assign():
        // It updates only licenseId / validFrom / endTo for the app row identified by slug.
        service.updateLicense(adminId, projectId, slug, req);

        return ResponseEntity.noContent().build();
    }

    /** Delete one app under owner+project by slug. */
    @DeleteMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<Void> removeApp(@PathVariable Long adminId,
                                          @PathVariable Long projectId,
                                          @PathVariable String slug) {

        // Deletes the AdminUserProject row (owner+project+slug).
        // It does not delete the project itself, and does not delete other app rows under same project.
        service.remove(adminId, projectId, slug);

        return ResponseEntity.noContent().build();
    }

    // -------- NEW: upload logo (multipart) --------

    /**
     * Upload a logo image for a specific app and store its URL in DB.
     *
     * Endpoint:
     * - POST /api/admin-users/{adminId}/projects/{projectId}/apps/{slug}/logo
     * - Content-Type: multipart/form-data
     *
     * Request:
     * - Part name: "file"
     *
     * Response:
     * - logoUrl: the public URL of the uploaded logo
     * - apkUrl: returned for convenience (frontend can refresh app card without another call)
     */
    @PostMapping(value = "/projects/{projectId}/apps/{slug}/logo", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadLogo(@PathVariable Long adminId,
                                                          @PathVariable Long projectId,
                                                          @PathVariable String slug,
                                                          @RequestPart("file") MultipartFile file) throws Exception {

        // 1) Store file to disk and update AdminUserProject.logoUrl
        String url = service.updateAppLogo(adminId, projectId, slug, file);

        // 2) Re-load link to return related info (like current apkUrl)
        var link = service.get(adminId, projectId, slug);

        // 3) Build small response map
        Map<String, Object> body = new HashMap<>();
        body.put("logoUrl", url);
        body.put("apkUrl", link.getApkUrl() == null ? "" : link.getApkUrl());

        return ResponseEntity.ok(body);
    }

    // -------- NEW: one-shot artifact getter (logo + apk + meta) --------

    /**
     * Fetch "artifact + metadata" for one app (owner+project+slug) in a single response.
     *
     * This is useful for screens like:
     * - App builder / CI output page
     * - Owner dashboard "download" section
     * - Mobile app "connect & download artifacts" wizard
     */
    @GetMapping("/projects/{projectId}/apps/{slug}/artifact")
    public ResponseEntity<AdminAppAssignmentResponse> artifact(@PathVariable Long adminId,
                                                               @PathVariable Long projectId,
                                                               @PathVariable String slug) {

        // Fetch entity by (adminId, projectId, slug)
        var l = service.get(adminId, projectId, slug);

        // Convert entity -> response DTO
        // Strings are normalized (null -> "") for frontend simplicity.
        return ResponseEntity.ok(new AdminAppAssignmentResponse(
                l.getProject().getId(),
                l.getProject().getProjectName(),
                l.getAppName() == null ? "" : l.getAppName(),
                l.getSlug(),
                l.getStatus() == null ? "" : l.getStatus(),
                l.getLicenseId() == null ? "" : l.getLicenseId(),
                l.getValidFrom(),
                l.getEndTo(),
                l.getThemeId(),
                l.getApkUrl() == null ? "" : l.getApkUrl(),
                l.getIpaUrl() == null ? "" : l.getIpaUrl(),
                l.getBundleUrl() == null ? "" : l.getBundleUrl(),
                l.getLogoUrl() == null ? "" : l.getLogoUrl(),
                l.getCurrency() != null ? l.getCurrency().getCode() : null,
                l.getCurrency() != null ? l.getCurrency().getSymbol() : null
        ));
    }

    // ---------------------------
    // BACKWARD-COMPAT SHIMS (optional)
    // ---------------------------

    /**
     * Legacy endpoint: older clients used "/projects" for listing.
     * Now it returns the same content as "/apps".
     */
    @Deprecated
    @GetMapping("/projects")
    public ResponseEntity<List<AdminAppAssignmentResponse>> legacyList(@PathVariable Long adminId) {
        return ResponseEntity.ok(service.list(adminId));
    }

    /**
     * Legacy endpoint: older clients posted a body containing projectId (and optionally slug/appName).
     * This delegates to service.assign(...) which supports both old and new styles.
     */
    @Deprecated
    @PostMapping("/projects")
    public ResponseEntity<Void> legacyAssign(@PathVariable Long adminId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        if (req.getProjectId() == null) return ResponseEntity.badRequest().build();
        service.assign(adminId, req);
        return ResponseEntity.noContent().build();
    }

    /**
     * Legacy endpoint: update license for a project using slug from request body.
     * New style puts slug in the path, but old clients can still call this.
     */
    @Deprecated
    @PutMapping("/projects/{projectId}")
    public ResponseEntity<Void> legacyUpdate(@PathVariable Long adminId,
                                             @PathVariable Long projectId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        if (req.getSlug() == null || req.getSlug().isBlank()) return ResponseEntity.badRequest().build();
        service.updateLicense(adminId, projectId, req.getSlug(), req);
        return ResponseEntity.noContent().build();
    }

    /**
     * Legacy endpoint: delete with slug passed as a query param (?slug=...).
     * New style puts slug in the path.
     */
    @Deprecated
    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> legacyRemove(@PathVariable Long adminId,
                                             @PathVariable Long projectId,
                                             @RequestParam(name = "slug", required = false) String slug) {
        if (slug == null || slug.isBlank()) return ResponseEntity.badRequest().build();
        service.remove(adminId, projectId, slug);
        return ResponseEntity.noContent().build();
    }
}
