// src/main/java/com/build4all/app/web/OwnerAppRequestController.java
package com.build4all.app.web;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
        return service.createRequest(
                ownerId,
                dto.projectId(),
                dto.appName(),
                dto.slug(),
                dto.logoUrl(),
                dto.themeId(),
                dto.notes()
        );
    }

    @GetMapping("/app-requests")
    public List<AppRequest> myRequests(@RequestParam Long ownerId) {
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping("/my-apps")
    public List<OwnerProjectView> myApps(@RequestParam Long ownerId) {
        return aupRepo.findOwnerProjectsSlim(ownerId);
    }
    
    @PostMapping("/app-requests/auto")
    public Object createAndAutoApprove(@RequestParam Long ownerId,
                                       @RequestBody CreateAppRequestDto dto) {
        return service.createAndAutoApprove(
                ownerId,
                dto.projectId(),
                dto.appName(),
                dto.slug(),
                dto.logoUrl(),
                dto.themeId(),
                dto.notes()
        );
    }

}
