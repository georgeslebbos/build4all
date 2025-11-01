package com.build4all.admin.web;

import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.service.AdminUserProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
// NOTE: We keep the same root but add app-aware routes below.
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

    /** Create or overwrite a single app under a project (owner+project+slug). 
     *  If slug is missing, it will be derived from appName; uniqueness ensured.
     */
    @PostMapping("/projects/{projectId}/apps")
    public ResponseEntity<Void> assignApp(@PathVariable Long adminId,
                                          @PathVariable Long projectId,
                                          @RequestBody AdminAppAssignmentRequest req) {
        // enforce projectId from path to avoid client mistakes
        req.setProjectId(projectId);
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

    // ---------------------------
    // BACKWARD-COMPAT SHIMS (optional)
    // These keep old routes compiling but now operate on "apps".
    // You can delete them after updating your frontend.
    // ---------------------------

    /** (Legacy) List under /projects - now returns all apps for the owner. */
    @Deprecated
    @GetMapping("/projects")
    public ResponseEntity<List<AdminAppAssignmentResponse>> legacyList(@PathVariable Long adminId) {
        return ResponseEntity.ok(service.list(adminId));
    }

    /** (Legacy) POST /projects expecting AdminProjectAssignmentRequest.
     *  Replace on client with POST /projects/{projectId}/apps and AdminAppAssignmentRequest.
     */
    @Deprecated
    @PostMapping("/projects")
    public ResponseEntity<Void> legacyAssign(@PathVariable Long adminId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        // Requires projectId in body for legacy; better to migrate to path {projectId}
        if (req.getProjectId() == null) {
            return ResponseEntity.badRequest().build();
        }
        service.assign(adminId, req);
        return ResponseEntity.noContent().build();
    }

    /** (Legacy) PUT /projects/{projectId} - requires slug in body to target an app. */
    @Deprecated
    @PutMapping("/projects/{projectId}")
    public ResponseEntity<Void> legacyUpdate(@PathVariable Long adminId,
                                             @PathVariable Long projectId,
                                             @RequestBody AdminAppAssignmentRequest req) {
        if (req.getSlug() == null || req.getSlug().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        service.updateLicense(adminId, projectId, req.getSlug(), req);
        return ResponseEntity.noContent().build();
    }

    /** (Legacy) DELETE /projects/{projectId} - requires slug in query/body; better to use /apps/{slug}. */
    @Deprecated
    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> legacyRemove(@PathVariable Long adminId,
                                             @PathVariable Long projectId,
                                             @RequestParam(name = "slug", required = false) String slug) {
        if (slug == null || slug.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        service.remove(adminId, projectId, slug);
        return ResponseEntity.noContent().build();
    }
}
