package com.build4all.app.web;

import com.build4all.app.domain.BuildPlatform;
import com.build4all.app.service.AppBuildJobService;
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
    private final AppBuildJobService jobService;

    @Value("${ci.callbackToken:}")
    private String token;

    public CiCallbackController(AppRequestService service, AppBuildJobService jobService) {
        this.service = service;
        this.jobService = jobService;
    }

    // =========================================================
    // ✅ NEW: STATUS CALLBACKS (always available)
    // =========================================================

    @PutMapping(value = "/build-jobs/running",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markRunning(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String buildId = nz(body.get("buildId"));
        Long linkId = parseLong(body.get("linkId"));
        BuildPlatform platform = parsePlatform(body.get("platform"));

        if (!buildId.isBlank()) {
            jobService.markRunningByBuildId(buildId);
            return ResponseEntity.ok(Map.of("message", "RUNNING saved", "buildId", buildId));
        }

        if (linkId != null && platform != null) {
            jobService.markLatestRunning(linkId, platform);
            return ResponseEntity.ok(Map.of("message", "RUNNING saved (latest)", "linkId", linkId, "platform", platform.name()));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "buildId OR (linkId+platform) required"));
    }

    @PutMapping(value = "/build-jobs/failed",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markFailed(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String buildId = nz(body.get("buildId"));
        Long linkId = parseLong(body.get("linkId"));
        BuildPlatform platform = parsePlatform(body.get("platform"));
        String error = nz(body.get("error"));
        if (error.isBlank()) error = "CI build failed";

        if (!buildId.isBlank()) {
            jobService.markFailedByBuildId(buildId, error);
            return ResponseEntity.ok(Map.of("message", "FAILED saved", "buildId", buildId));
        }

        if (linkId != null && platform != null) {
            jobService.markLatestFailed(linkId, platform, error);
            return ResponseEntity.ok(Map.of("message", "FAILED saved (latest)", "linkId", linkId, "platform", platform.name()));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "buildId OR (linkId+platform) required"));
    }

    @PutMapping(value = "/build-jobs/succeeded",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markSucceeded(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String buildId = nz(body.get("buildId"));
        Long linkId = parseLong(body.get("linkId"));
        BuildPlatform platform = parsePlatform(body.get("platform"));

        if (!buildId.isBlank()) {
            jobService.markSucceededByBuildId(buildId);
            return ResponseEntity.ok(Map.of("message", "SUCCESS saved", "buildId", buildId));
        }

        if (linkId != null && platform != null) {
            jobService.markLatestSucceeded(linkId, platform);
            return ResponseEntity.ok(Map.of("message", "SUCCESS saved (latest)", "linkId", linkId, "platform", platform.name()));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "buildId OR (linkId+platform) required"));
    }

    // =========================================================
    // Existing endpoints (APK/AAB/IPA) — keep as-is
    // =========================================================

    @PutMapping(value = "/owner-project-links/{linkId}/apk-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setApkFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String apkUrl = required(body, "apkUrl");
        String buildId = nz(body.get("buildId"));

        service.setApkUrlByLinkId(linkId, apkUrl);

        if (!buildId.isBlank()) jobService.recordAndroidApkByBuildId(buildId, apkUrl);
        else jobService.recordLatestAndroidApk(linkId, apkUrl);

        log.info("CI saved apkUrl linkId={} buildId={}", linkId, (buildId.isBlank() ? "N/A" : buildId));

        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "linkId", linkId,
                "apkUrl", apkUrl,
                "buildId", buildId
        ));
    }

    @PutMapping(value = "/owner-project-links/{linkId}/aab-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setAabFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String aabUrl = required(body, "aabUrl");
        String buildId = nz(body.get("buildId"));

        service.setBundleUrlByLinkId(linkId, aabUrl);

        if (!buildId.isBlank()) jobService.markAndroidAabSucceededByBuildId(buildId, aabUrl);
        else jobService.markLatestAndroidAabSucceeded(linkId, aabUrl);

        log.info("CI saved aabUrl linkId={} buildId={}", linkId, (buildId.isBlank() ? "N/A" : buildId));

        return ResponseEntity.ok(Map.of(
                "message", "AAB URL saved",
                "linkId", linkId,
                "aabUrl", aabUrl,
                "buildId", buildId
        ));
    }

    @PutMapping(value = "/owner-project-links/{linkId}/ipa-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setIpaFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String ipaUrl = required(body, "ipaUrl");
        String buildId = nz(body.get("buildId"));

        service.setIpaUrlByLinkId(linkId, ipaUrl);

        if (!buildId.isBlank()) jobService.markIosIpaSucceededByBuildId(buildId, ipaUrl);
        else jobService.markLatestIosIpaSucceeded(linkId, ipaUrl);

        log.info("CI saved ipaUrl linkId={} buildId={}", linkId, (buildId.isBlank() ? "N/A" : buildId));

        return ResponseEntity.ok(Map.of(
                "message", "IPA URL saved",
                "linkId", linkId,
                "ipaUrl", ipaUrl,
                "buildId", buildId
        ));
    }

    // ---------------- helpers ----------------

    private boolean isAuthorized(String xToken, String authHeader) {
        if (token == null || token.isBlank()) return false;

        if (xToken != null && token.equals(xToken.trim())) return true;

        if (authHeader != null) {
            String a = authHeader.trim();
            if (a.toLowerCase().startsWith("bearer ")) a = a.substring(7).trim();
            return token.equals(a);
        }
        return false;
    }

    private static String required(Map<String, String> body, String key) {
        String v = body.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(key + " required");
        return v;
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static Long parseLong(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static BuildPlatform parsePlatform(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return BuildPlatform.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
