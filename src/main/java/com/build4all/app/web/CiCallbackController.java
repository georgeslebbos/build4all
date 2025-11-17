package com.build4all.app.web;

import com.build4all.app.service.AppRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ci")
public class CiCallbackController {

    private static final Logger log = LoggerFactory.getLogger(CiCallbackController.class);

    private final AppRequestService service;

    @Value("${ci.callbackToken:}")
    private String token;

    public CiCallbackController(AppRequestService service) {
        this.service = service;
    }

    /** Save APK URL by (ownerId, projectId, slug). */
    @PutMapping(
        value = "/owner-projects/{ownerId}/{projectId}/apps/{slug}/apk-url",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setApkFromCiBySlug(
            @RequestHeader(value = "X-Auth-Token", required = false) String t,
            @PathVariable Long ownerId,
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(t)) {
            log.warn("CI callback unauthorized (slug path): ownerId={}, projectId={}, slug={}", ownerId, projectId, slug);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String apkUrl = body.get("apkUrl");
        if (apkUrl == null || apkUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apkUrl required"));
        }

        var link = service.setApkUrl(ownerId, projectId, slug, apkUrl);
        log.info("CI saved apkUrl via slug ownerId={}, projectId={}, slug={}, url={}", ownerId, projectId, slug, apkUrl);

        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "ownerId", ownerId,
                "projectId", projectId,
                "slug", slug,
                "apkUrl", link.getApkUrl()
        ));
    }

    /** Save APK URL by AdminUserProject primary key (recommended for CI). */
    @PutMapping(
        value = "/owner-project-links/{linkId}/apk-url",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setApkFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String t,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(t)) {
            log.warn("CI callback unauthorized (linkId path): linkId={}", linkId);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String apkUrl = body.get("apkUrl");
        if (apkUrl == null || apkUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apkUrl required"));
        }

        service.setApkUrlByLinkId(linkId, apkUrl);
        log.info("CI saved apkUrl via linkId={} url={}", linkId, apkUrl);

        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "linkId", linkId,
                "apkUrl", apkUrl
        ));
    }

    // -------- helpers --------

    private boolean isAuthorized(String headerToken) {
        boolean ok = token != null && !token.isBlank() && token.equals(headerToken);
        if (!ok) {
            log.warn("Invalid CI callback token received: {}", headerToken);
        }
        return ok;
    }
    
    /** Save AAB URL (bundle) by AdminUserProject primary key (for CI AAB workflow). */
    @PutMapping(
        value = "/owner-project-links/{linkId}/aab-url",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setAabFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String t,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(t)) {
            log.warn("CI callback unauthorized (AAB, linkId path): linkId={}", linkId);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String aabUrl = body.get("aabUrl");
        if (aabUrl == null || aabUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "aabUrl required"));
        }

        service.setBundleUrlByLinkId(linkId, aabUrl);
        log.info("CI saved aabUrl via linkId={} url={}", linkId, aabUrl);

        return ResponseEntity.ok(Map.of(
                "message", "AAB URL saved",
                "linkId", linkId,
                "aabUrl", aabUrl
        ));
    }

}
