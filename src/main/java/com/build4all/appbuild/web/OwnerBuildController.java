package com.build4all.appbuild.web;

import com.build4all.app.service.AppRequestService;
import com.build4all.appbuild.service.OwnerAppBuildService;
import com.build4all.security.JwtUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/owner/builds")
public class OwnerBuildController {

    private final OwnerAppBuildService service;
    private final JwtUtil jwt;
    private final AppRequestService appRequestService;

    public OwnerBuildController(OwnerAppBuildService service, JwtUtil jwt, AppRequestService appRequestService) {
        this.service = service;
        this.jwt = jwt;
        this.appRequestService = appRequestService;
    }

    public record BuildApkRequest(
            Long ownerProjectLinkId,  // preferred: we can persist by row id
            Long projectId,           // used if you prefer slug mode
            String slug,              // slug used with ownerId+projectId
            String appName,
            String apiBaseUrl,
            String wsPath,
            String appRole,
            String ownerAttachMode,
            String appLogoUrl
    ) {}

    @PostMapping("/apk")
    public ResponseEntity<?> buildApk(@RequestHeader("Authorization") String auth,
                                      @RequestBody BuildApkRequest req) {
        try {
            String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
            if (!jwt.isAdminOrOwner(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden"));
            }
            Long ownerId = jwt.extractId(token);

            var result = service.buildOwnerApk(new OwnerAppBuildService.BuildRequest(
                    ownerId,
                    req.ownerProjectLinkId() == null ? 0L : req.ownerProjectLinkId(),
                    req.projectId(),
                    req.appName(),
                    req.apiBaseUrl(),
                    req.wsPath() == null ? "/api/ws" : req.wsPath(),
                    req.appRole() == null ? "both" : req.appRole(),
                    req.ownerAttachMode() == null ? "header" : req.ownerAttachMode(),
                    req.appLogoUrl()
            ));

            // 🔴 persist RELATIVE url to DB
            if (req.ownerProjectLinkId() != null) {
                appRequestService.setApkUrlByLinkId(req.ownerProjectLinkId(), result.relUrl());
            } else if (req.projectId() != null && req.slug() != null && !req.slug().isBlank()) {
                appRequestService.setApkUrlByOwnerProjectSlug(
                        ownerId, req.projectId(), req.slug().trim().toLowerCase(), result.relUrl());
            } // else: insufficient identifiers to persist

            // Return both, but DB stores only the relative one
            return ResponseEntity.ok(Map.of(
                    "message", "Build completed",
                    "apkUrl", result.publicUrl(),  // full public URL
                    "apkRelUrl", result.relUrl(),  // relative path saved in DB
                    "builtAt", result.builtAt().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Build failed", "error", e.getMessage()));
        }
    }
}
