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

 // src/main/java/com/build4all/appbuild/web/OwnerBuildController.java

    @PostMapping("/apk")
    public ResponseEntity<?> buildApk(@RequestHeader("Authorization") String auth,
                                      @RequestBody BuildApkRequest req) {
        try {
            String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();

           
            if (!jwt.isAdminOrOwner(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Forbidden"));
            }

            Long callerId = jwt.extractId(token);

            var result = service.buildOwnerApk(new OwnerAppBuildService.BuildRequest(
                    callerId,
                    req.ownerProjectLinkId() == null ? 0L : req.ownerProjectLinkId(),
                    req.projectId(),
                    req.appName(),
                    req.apiBaseUrl(),
                    req.wsPath() == null ? "/api/ws" : req.wsPath(),
                    req.appRole() == null ? "both" : req.appRole(),
                    req.ownerAttachMode() == null ? "header" : req.ownerAttachMode(),
                    req.appLogoUrl()
            ));

            boolean saved = false;

          
            if (req.ownerProjectLinkId() != null) {
               
                try {
                    appRequestService.setApkUrlByLinkId(callerId, req.ownerProjectLinkId(), result.relUrl());
                    saved = true;
                } catch (SecurityException se) {
                                      appRequestService.setApkUrlByLinkId(req.ownerProjectLinkId(), result.relUrl());
                    saved = true;
                }
            }
       
            else if (req.projectId() != null && req.slug() != null && !req.slug().isBlank()) {
                try {
                    appRequestService.setApkUrlByOwnerProjectSlug(
                            callerId, req.projectId(), req.slug().trim().toLowerCase(), result.relUrl());
                    saved = true;
                } catch (SecurityException se) {
                 
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                            "message", "Provide ownerProjectLinkId when caller is not the owner",
                            "hint", "Send ownerProjectLinkId or call /owner-project-links/{linkId}/apk-url from CI"
                    ));
                }
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Missing identifiers",
                        "needOneOf", "ownerProjectLinkId OR (projectId + slug)"
                ));
            }

            if (!saved) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "message", "Build completed but DB not updated",
                        "hint", "Pass ownerProjectLinkId to ensure persistence"
                ));
            }

 
            return ResponseEntity.ok(Map.of(
                    "message", "Build completed",
                    "apkUrl", result.publicUrl(),   
                    "apkRelUrl", result.relUrl(), 
                    "builtAt", result.builtAt().toString()
            ));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Forbidden", "error", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Build failed", "error", e.getMessage()));
        }
    }

    @PostMapping("/ios")
    public ResponseEntity<?> saveIpa(@RequestHeader("Authorization") String auth,
                                     @RequestBody Map<String, Object> body) {
        String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (!jwt.isAdminOrOwner(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));
        }
        Long ownerId = jwt.extractId(token);

        Long linkId   = body.get("ownerProjectLinkId") instanceof Number n ? n.longValue() : null;
        Long projectId= body.get("projectId") instanceof Number n2 ? n2.longValue() : null;
        String slug   = (String) body.get("slug");
        String ipaRel = (String) body.get("ipaRelUrl"); // must start with /uploads/

        if (ipaRel == null || ipaRel.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ipaRelUrl is required"));
        }

        if (linkId != null) {
            appRequestService.setIpaUrlByLinkId(ownerId, linkId, ipaRel);
        } else if (projectId != null && slug != null && !slug.isBlank()) {
            appRequestService.setIpaUrlByOwnerProjectSlug(ownerId, projectId, slug.trim().toLowerCase(), ipaRel);
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Provide ownerProjectLinkId or (projectId+slug)"));
        }

        String publicBaseUrl = System.getProperty("uploads.public-base-url",
                System.getenv().getOrDefault("UPLOADS_PUBLIC_BASE_URL", ""));
        String publicUrl = publicBaseUrl.replaceAll("/+$","") + ipaRel;

        return ResponseEntity.ok(Map.of(
                "message", "iOS IPA saved",
                "ipaUrl", publicUrl,
                "ipaRelUrl", ipaRel
        ));
    }
    
    public record BuildAabRequest(
            Long ownerProjectLinkId,
            Long projectId,
            String slug,
            String appName,
            String apiBaseUrl,
            String wsPath,
            String appRole,
            String ownerAttachMode,
            String appLogoUrl,
            Integer versionCode, // optional
            String  versionName  // optional (handled inside Flutter normally)
    ) {}

    @PostMapping("/aab")
    public ResponseEntity<?> buildAab(@RequestHeader("Authorization") String auth,
                                      @RequestBody BuildAabRequest req) {
        try {
            String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
            if (!jwt.isAdminOrOwner(token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Forbidden"));
            }
            Long ownerId = jwt.extractId(token);

            var result = service.buildOwnerAab(new OwnerAppBuildService.BuildRequest(
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

            // persist RELATIVE bundle url in DB
            if (req.ownerProjectLinkId() != null) {
                appRequestService.setBundleUrlByLinkId(ownerId, req.ownerProjectLinkId(), result.relUrl());
            } else if (req.projectId() != null && req.slug() != null && !req.slug().isBlank()) {
                appRequestService.setBundleUrlByOwnerProjectSlug(
                        ownerId, req.projectId(), req.slug().trim().toLowerCase(), result.relUrl());
            } // else can't persist

            return ResponseEntity.ok(Map.of(
                    "message", "Build completed",
                    "bundleUrl", result.publicUrl(),  // full public URL
                    "bundleRelUrl", result.relUrl(),  // relative path saved in DB
                    "builtAt", result.builtAt().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Build failed", "error", e.getMessage()));
        }
    }

}
