package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ci/config")
public class CiConfigController {

    private final AdminUserProjectRepository aupRepo;

    public CiConfigController(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    // Static runtime values for the mobile app (pulled from application.properties)
    @Value("${mobile.apiBaseUrl:}")
    private String apiBaseUrl;

    @Value("${mobile.wsPath:/api/ws}")
    private String wsPath;

    @Value("${mobile.ownerAttachMode:header}")
    private String ownerAttachMode;

    @Value("${mobile.appRole:both}")
    private String appRole;

    @Value("${ci.callbackUrl:}")
    private String callbackBase; // ends with /api/ci

    @Value("${ci.callbackToken:}")
    private String callbackToken;

    @GetMapping(
        value = "/owner-projects/{ownerId}/{projectId}/apps/{slug}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> getConfigBySlug(
            @PathVariable Long ownerId,
            @PathVariable Long projectId,
            @PathVariable String slug) {

        AdminUserProject link = aupRepo
            .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slug.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ownerId", link.getAdmin().getAdminId());
        m.put("projectId", link.getProject().getId());
        m.put("ownerProjectLinkId", String.valueOf(link.getId()));
        m.put("slug", n(link.getSlug()));
        m.put("appName", n(link.getAppName()));
        m.put("appLogoUrl", n(link.getLogoUrl()));
        m.put("themeId", link.getThemeId());

        // runtime values consumed by the Flutter app at build-time
        m.put("apiBaseUrl", n(apiBaseUrl));
        m.put("wsPath", n(wsPath));
        m.put("appRole", n(appRole));
        m.put("ownerAttachMode", n(ownerAttachMode));

        // callback details for the Action to PUT back apkUrl
        m.put("callbackBase", n(callbackBase));
        m.put("callbackToken", n(callbackToken));

        return ResponseEntity.ok(m);
    }

    private static String n(String s) { return s == null ? "" : s; }
}
