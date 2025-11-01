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
public class AdminAppRequestController {

    private final AppRequestRepository appRequestRepo;
    private final AppRequestService service;

    public AdminAppRequestController(AppRequestRepository appRequestRepo,
                                     AppRequestService service) {
        this.appRequestRepo = appRequestRepo;
        this.service = service;
    }

    @GetMapping("/app-requests")
    public List<AppRequest> list(@RequestParam(defaultValue = "PENDING") String status) {
        return appRequestRepo.findByStatusOrderByCreatedAtAsc(status);
    }

    @PostMapping("/app-requests/{id}/approve")
    public ApproveResponseDto approve(@PathVariable Long id) {
        AdminUserProject link = service.approve(id);
        return new ApproveResponseDto(link.getAdminId(), link.getProjectId(), link.getSlug());
    }

    @PostMapping("/app-requests/{id}/reject")
    public void reject(@PathVariable Long id) {
        service.reject(id);
    }

    // ✅ now app-scoped (slug in path)
    @PutMapping("/owner-projects/{adminId}/{projectId}/apps/{slug}/apk-url")
    public AdminUserProject setApk(@PathVariable Long adminId,
                                   @PathVariable Long projectId,
                                   @PathVariable String slug,
                                   @RequestBody SetApkUrlDto body) {
        return service.setApkUrl(adminId, projectId, slug, body.apkUrl());
    }
}
