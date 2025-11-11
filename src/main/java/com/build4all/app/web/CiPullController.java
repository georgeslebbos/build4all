package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.app.service.ApkManifestPullService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ci")
public class CiPullController {

    private final ApkManifestPullService service;

    public CiPullController(ApkManifestPullService service) {
        this.service = service;
    }

  
    @PostMapping(
        value = "/pull/{ownerId}/{projectId}/{slug}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> pullOnce(
            @PathVariable long ownerId,
            @PathVariable long projectId,
            @PathVariable String slug
    ) {
        AdminUserProject link = service.updateLinkFromManifest(ownerId, projectId, slug);
        return ResponseEntity.ok(Map.of(
                "message", "apkUrl updated",
                "ownerId", ownerId,
                "projectId", projectId,
                "slug", slug,
                "apkUrl", link.getApkUrl()
        ));
    }
}
