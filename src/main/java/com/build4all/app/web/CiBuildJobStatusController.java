package com.build4all.app.web;

import com.build4all.app.service.AppBuildJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ci/build-jobs")
public class CiBuildJobStatusController {

    private static final Logger log = LoggerFactory.getLogger(CiBuildJobStatusController.class);

    private final AppBuildJobService jobService;

    @Value("${ci.callbackToken:}")
    private String token;

    public CiBuildJobStatusController(AppBuildJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping(value = "/{buildId}/running", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markRunning(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String buildId
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        jobService.markRunningByBuildId(buildId);
        log.info("CI marked RUNNING buildId={}", buildId);
        return ResponseEntity.ok(Map.of("message", "Build marked RUNNING", "buildId", buildId));
    }

    @PostMapping(value = "/{buildId}/failed", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markFailed(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String buildId,
            @RequestBody Map<String, String> body
    ) {
        if (!isAuthorized(xToken, auth)) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String error = body.getOrDefault("error", "CI build failed");
        jobService.markFailedByBuildId(buildId, error);

        log.info("CI marked FAILED buildId={}", buildId);
        return ResponseEntity.ok(Map.of("message", "Build marked FAILED", "buildId", buildId));
    }

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
}
