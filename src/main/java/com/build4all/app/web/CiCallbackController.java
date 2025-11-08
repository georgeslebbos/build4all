package com.build4all.app.web;

import com.build4all.app.service.AppRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ci")
public class CiCallbackController {

    private final AppRequestService service;

    @Value("${ci.callbackToken:}")
    private String token;

    public CiCallbackController(AppRequestService service) {
        this.service = service;
    }

    @PutMapping("/owner-projects/{ownerId}/{projectId}/apps/{slug}/apk-url")
    public ResponseEntity<?> setApkFromCi(
            @RequestHeader(value = "X-Auth-Token", required = false) String t,
            @PathVariable Long ownerId,
            @PathVariable Long projectId,
            @PathVariable String slug,
            @RequestBody Map<String, String> body) {

        if (token == null || token.isBlank() || !token.equals(t)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        String apkUrl = body.get("apkUrl");
        if (apkUrl == null || apkUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apkUrl required"));
        }

        var link = service.setApkUrl(ownerId, projectId, slug, apkUrl);
        return ResponseEntity.ok(Map.of(
                "message", "APK URL saved",
                "ownerId", ownerId,
                "projectId", projectId,
                "slug", slug,
                "apkUrl", link.getApkUrl()
        ));
    }
}
