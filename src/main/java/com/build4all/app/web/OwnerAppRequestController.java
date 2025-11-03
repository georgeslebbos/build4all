package com.build4all.app.web;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/owner")
public class OwnerAppRequestController {

    private final AppRequestService service;
    private final AppRequestRepository appRequestRepo;
    private final AdminUserProjectRepository aupRepo;

    public OwnerAppRequestController(AppRequestService service,
                                     AppRequestRepository appRequestRepo,
                                     AdminUserProjectRepository aupRepo) {
        this.service = service;
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
    }

    @PostMapping("/app-requests")
    public ResponseEntity<?> create(@RequestParam Long ownerId,
                                    @RequestBody CreateAppRequestDto dto) {
        try {
            AppRequest r = service.createRequest(
                    ownerId,
                    dto.projectId(),
                    dto.appName(),
                    dto.slug(),
                    dto.logoUrl(),
                    dto.themeId(),
                    dto.notes()
            );
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Request created");
            body.put("requestId", r.getId());
            body.put("status", r.getStatus());
            body.put("appName", nz(r.getAppName()));
            body.put("slug", nz(r.getSlug()));
            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Internal error");
            err.put("details", ex.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @PostMapping("/app-requests/auto")
    public ResponseEntity<?> createAndAutoApprove(@RequestParam Long ownerId,
                                                  @RequestBody CreateAppRequestDto dto) {
        try {
            AdminUserProject link = service.createAndAutoApprove(
                    ownerId, dto.projectId(), dto.appName(), dto.slug(),
                    dto.logoUrl(), dto.themeId(), dto.notes());

            Map<String, Object> body = new HashMap<>();
            body.put("message", "APK build started");
            // ✅ use the values you already have
            body.put("adminId", ownerId);
            body.put("projectId", dto.projectId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("status", nz(link.getStatus()));
            body.put("licenseId", nz(link.getLicenseId()));
            body.put("themeId", link.getThemeId());
            body.put("validFrom", link.getValidFrom());
            body.put("endTo", link.getEndTo());
            body.put("apkUrl", nz(link.getApkUrl()));
            return ResponseEntity.ok(body);


        } catch (IllegalArgumentException | IllegalStateException ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Bad request");
            err.put("details", ex.getMessage());
            return ResponseEntity.badRequest().body(err);

        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Internal error");
            err.put("details", ex.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @GetMapping("/app-requests")
    public List<AppRequest> myRequests(@RequestParam Long ownerId) {
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping("/my-apps")
    public List<OwnerProjectView> myApps(@RequestParam Long ownerId) {
        return aupRepo.findOwnerProjectsSlim(ownerId);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
