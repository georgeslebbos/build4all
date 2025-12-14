package com.build4all.admin.web;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.ApproveResponseDto;
import com.build4all.admin.dto.SetApkUrlDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
/**
 * Admin endpoints for handling "App Requests" workflow.
 *
 * What is an AppRequest?
 * - Typically a request created by an owner/business to generate/enable an app instance.
 * - Admin can list requests, approve them (creating/updating AdminUserProject link),
 *   or reject them.
 *
 * Also includes an endpoint to update the APK URL for a specific app instance (AdminUserProject),
 * scoped by (adminId, projectId, slug).
 */
public class AdminAppRequestController {

    // Direct repository access for simple list query.
    private final AppRequestRepository appRequestRepo;

    // Service contains the business logic (approve/reject/setApkUrl).
    private final AppRequestService service;

    public AdminAppRequestController(AppRequestRepository appRequestRepo,
                                     AppRequestService service) {
        this.appRequestRepo = appRequestRepo;
        this.service = service;
    }

    /**
     * List app requests filtered by status (default: PENDING).
     *
     * Example:
     * GET /api/admin/app-requests?status=PENDING
     *
     * Returns AppRequest entities ordered by oldest first (createdAt asc),
     * so admins process them in order.
     */
    @GetMapping("/app-requests")
    public List<AppRequest> list(@RequestParam(defaultValue = "PENDING") String status) {
        return appRequestRepo.findByStatusOrderByCreatedAtAsc(status);
    }

    /**
     * Approve an app request.
     *
     * Flow (inside service.approve):
     * - Validate request exists and is approvable
     * - Create or update the AdminUserProject link (the actual "app assignment")
     * - Mark request as APPROVED
     *
     * Returns a minimal response containing identifiers used by the frontend:
     * adminId, projectId, slug.
     */
    @PostMapping("/app-requests/{id}/approve")
    public ApproveResponseDto approve(@PathVariable Long id) {
        AdminUserProject link = service.approve(id);
        return new ApproveResponseDto(link.getAdminId(), link.getProjectId(), link.getSlug());
    }

    /**
     * Reject an app request.
     *
     * Flow (inside service.reject):
     * - Validate request exists
     * - Mark request as REJECTED (or delete it depending on your implementation)
     */
    @PostMapping("/app-requests/{id}/reject")
    public void reject(@PathVariable Long id) {
        service.reject(id);
    }

    // ✅ now app-scoped (slug in path)
    /**
     * Update the APK URL for a specific app instance.
     *
     * Why include slug?
     * - Because one admin + one project can have multiple apps (distinguished by slug),
     *   so (adminId, projectId, slug) uniquely identifies one AdminUserProject row.
     *
     * Example:
     * PUT /api/admin/owner-projects/{adminId}/{projectId}/apps/{slug}/apk-url
     * Body: { "apkUrl": "https://..." }
     *
     * Returns the updated AdminUserProject entity.
     */
    @PutMapping("/owner-projects/{adminId}/{projectId}/apps/{slug}/apk-url")
    public AdminUserProject setApk(@PathVariable Long adminId,
                                   @PathVariable Long projectId,
                                   @PathVariable String slug,
                                   @RequestBody SetApkUrlDto body) {
        // body.apkUrl() comes from the record accessor (SetApkUrlDto is a Java record).
        return service.setApkUrl(adminId, projectId, slug, body.apkUrl());
    }
}
