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
public class AdminUserProjectsController {

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
        return ResponseEntity.ok(service.list(adminId));
    }

    /** Create or overwrite a single app under a project (owner+project+slug). */
    @PostMapping("/projects/{projectId}/apps")
    public ResponseEntity<Void> assignApp(@PathVariable Long adminId,
                                          @PathVariable Long projectId,
                                          @RequestBody AdminAppAssignmentRequest req) {
        req.setProjectId(projectId); // enforce path projectId
        service.assign(adminId, req);
        return ResponseEntity.noContent().build();
    }

    /** Update only license/validity for an existing app (owner+project+slug). */
    @PutMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<Void> updateAppLicense(@PathVariable Long adminId,
                                                 @PathVariable Long projectId,
                                                 @PathVariable String slug,
                                                 @RequestBody AdminAppAssignmentRequest req) {
        service.updateLicense(adminId, projectId, slug, req);
        return ResponseEntity.noContent().build();
    }

    /** Delete one app under owner+project by slug. */
    @DeleteMapping("/projects/{projectId}/apps/{slug}")
    public ResponseEntity<Void> removeApp(@PathVariable Long adminId,
                                          @PathVariable Long projectId,
                                          @PathVariable String slug) {
        service.remove(adminId, projectId, slug);
        return ResponseEntity.noContent().build();
    }

    // -------- NEW: upload logo (multipart) --------
    @PostMapping(value = "/projects/{projectId}/apps/{slug}/logo", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadLogo(@PathVariable Long adminId,
                                                          @PathVariable Long projectId,
                                                          @PathVariable String slug,
                                                          @RequestPart("file") MultipartFile file) throws Exception {
        String url = service.updateAppLogo(adminId, projectId, slug, file);

        var link = service.get(adminId, projectId, slug); // includes current apkUrl
        Map<String, Object> body = new HashMap<>();
        body.put("logoUrl", url);
        body.put("apkUrl", link.getApkUrl() == null ? "" : link.getApkUrl());
        return ResponseEntity.ok(body);
    }

    // -------- NEW: one-shot artifact getter (logo + apk + meta) --------
    @GetMapping("/projects/{projectId}/apps/{slug}/artifact")
    public ResponseEntity<AdminAppAssignmentResponse> artifact(@PathVariable Long adminId,
                                                               @PathVariable Long projectId,
                                                               @PathVariable String slug) {
        var l = service.get(adminId, projectId, slug);
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

    @Deprecated
    @GetMapping("/projects")
    public ResponseEntity<List<AdminAppAssignmentResponse>> legacyList(@PathVariable Long adminId) {
        return ResponseEntity.ok(service.list(adminId));
    }

    @Deprecated
    @PostMapping("/projects")
    public ResponseEntity<Void> legacyAssign(@PathVariable Long adminId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        if (req.getProjectId() == null) return ResponseEntity.badRequest().build();
        service.assign(adminId, req);
        return ResponseEntity.noContent().build();
    }

    @Deprecated
    @PutMapping("/projects/{projectId}")
    public ResponseEntity<Void> legacyUpdate(@PathVariable Long adminId,
                                             @PathVariable Long projectId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        if (req.getSlug() == null || req.getSlug().isBlank()) return ResponseEntity.badRequest().build();
        service.updateLicense(adminId, projectId, req.getSlug(), req);
        return ResponseEntity.noContent().build();
    }

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
