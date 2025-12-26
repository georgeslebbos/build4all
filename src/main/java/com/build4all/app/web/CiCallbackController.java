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

    // ---------------- APK ----------------

    /** Save APK URL by (ownerId, projectId, slug). */
    @PutMapping(
            value = "/owner-projects/{ownerId}/{projectId}/apps/{slug}/apk-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setApkFromCiBySlug(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long ownerId,
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(xToken, auth)) {
            log.warn("CI callback unauthorized (apk slug): ownerId={} projectId={} slug={}", ownerId, projectId, slug);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String apkUrl = required(body, "apkUrl");
        var link = service.setApkUrl(ownerId, projectId, slug, apkUrl);

        log.info("CI saved apkUrl via slug ownerId={} projectId={} slug={}", ownerId, projectId, slug);

        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "ownerId", ownerId,
                "projectId", projectId,
                "slug", slug,
                "apkUrl", nz(link.getApkUrl())
        ));
    }

    /** Save APK URL by AdminUserProject primary key (recommended for CI). */
    @PutMapping(
            value = "/owner-project-links/{linkId}/apk-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setApkFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(xToken, auth)) {
            log.warn("CI callback unauthorized (apk linkId): linkId={}", linkId);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String apkUrl = required(body, "apkUrl");
        service.setApkUrlByLinkId(linkId, apkUrl);

        log.info("CI saved apkUrl via linkId={}", linkId);

        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "linkId", linkId,
                "apkUrl", apkUrl
        ));
    }

    // ---------------- AAB ----------------

    /** Save AAB URL (bundle) by AdminUserProject primary key. */
    @PutMapping(
            value = "/owner-project-links/{linkId}/aab-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setAabFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(xToken, auth)) {
            log.warn("CI callback unauthorized (aab linkId): linkId={}", linkId);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String aabUrl = required(body, "aabUrl");
        service.setBundleUrlByLinkId(linkId, aabUrl);

        log.info("CI saved aabUrl via linkId={}", linkId);

        return ResponseEntity.ok(Map.of(
                "message", "AAB URL saved",
                "linkId", linkId,
                "aabUrl", aabUrl
        ));
    }

    // ---------------- IPA ----------------

    /** Save IPA URL by AdminUserProject primary key. */
    @PutMapping(
            value = "/owner-project-links/{linkId}/ipa-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> setIpaFromCiByLinkId(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(xToken, auth)) {
            log.warn("CI callback unauthorized (ipa linkId): linkId={}", linkId);
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String ipaUrl = required(body, "ipaUrl");
        // you already have: setIpaUrlByLinkId(ownerId,...). Here we're using "no-owner-check" style like apkUrl.
        // If you want owner-check too, we can add a second endpoint.
        service.setIpaUrlByLinkId(linkId, ipaUrl); // make sure AppRequestService has this overload OR change to your existing method

        log.info("CI saved ipaUrl via linkId={}", linkId);

        return ResponseEntity.ok(Map.of(
                "message", "IPA URL saved",
                "linkId", linkId,
                "ipaUrl", ipaUrl
        ));
    }

    // ---------------- helpers ----------------

    private boolean isAuthorized(String xToken, String authHeader) {
        if (token == null || token.isBlank()) {
            log.warn("ci.callbackToken is blank; rejecting callbacks.");
            return false;
        }

        // 1) X-Auth-Token
        if (xToken != null && token.equals(xToken.trim())) return true;

        // 2) Authorization: Bearer <token>
        if (authHeader != null) {
            String a = authHeader.trim();
            if (a.toLowerCase().startsWith("bearer ")) a = a.substring(7).trim();
            if (token.equals(a)) return true;
        }

        // don’t log the secret token, just say it's wrong
        log.warn("Invalid CI callback token received (masked).");
        return false;
    }

    private static String required(Map<String, String> body, String key) {
        String v = body.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(key + " required");
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
