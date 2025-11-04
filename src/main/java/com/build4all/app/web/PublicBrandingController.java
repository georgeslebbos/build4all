package com.build4all.app.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicBrandingController {

    private final AdminUserProjectRepository aupRepo;

    public PublicBrandingController(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    @GetMapping("/mobile-branding")
    public Map<String, Object> branding(@RequestParam Long ownerId,
                                        @RequestParam Long projectId,
                                        @RequestParam String slug) {
        var link = aupRepo.findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slug)
                .orElseThrow(() -> new IllegalArgumentException("App not found"));

        String displayName = (link.getAppName() != null && !link.getAppName().isBlank())
                ? link.getAppName() : "HobbySphere";

        String logoUrl = link.getLogoUrl() == null ? "" : link.getLogoUrl();

        return Map.of(
                "displayName", displayName,
                "logoUrl", logoUrl
        );
    }
}
