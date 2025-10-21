package com.build4all.app.web;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Owner-facing endpoints:
 *  - Create a new app request
 *  - List my requests
 *  - List my app instances (admin_user_projects)
 *
 * NOTE: For brevity, ownerId is passed as a query param.
 * In production, resolve it from JWT.
 */
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
    public AppRequest create(@RequestParam Long ownerId, @RequestBody CreateAppRequestDto dto) {
        return service.createRequest(ownerId, dto.projectId(), dto.appName(), dto.notes());
    }

    @GetMapping("/app-requests")
    public List<AppRequest> myRequests(@RequestParam Long ownerId) {
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping("/my-apps")
    public List<AdminUserProject> myApps(@RequestParam Long ownerId) {
        return aupRepo.findByAdmin_AdminId(ownerId);
    }
}
