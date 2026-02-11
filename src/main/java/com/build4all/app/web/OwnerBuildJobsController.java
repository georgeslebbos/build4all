package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.domain.BuildPlatform;
import com.build4all.app.dto.AppBuildJobDto;
import com.build4all.app.repository.AppBuildJobRepository;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/apps")
public class OwnerBuildJobsController {

    private final AdminUserProjectRepository aupRepo;
    private final AppBuildJobRepository jobRepo;
    private final JwtUtil jwtUtil;

    public OwnerBuildJobsController(AdminUserProjectRepository aupRepo, AppBuildJobRepository jobRepo, JwtUtil jwtUtil) {
        this.aupRepo = aupRepo;
        this.jobRepo = jobRepo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping(value = "/{linkId}/build-jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AppBuildJobDto> list(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId
    ) {
        requireOwnerLinkAccess(authHeader, linkId);

        return jobRepo.findTop20ByApp_IdOrderByCreatedAtDesc(linkId).stream()
                .map(j -> new AppBuildJobDto(
                        j.getId(),
                        j.getApp() != null ? j.getApp().getId() : null,
                        j.getPlatform() != null ? j.getPlatform().name() : null,
                        j.getStatus() != null ? j.getStatus().name() : null,
                        nz(j.getCiBuildId()),
                        j.getCreatedAt(),
                        j.getStartedAt(),
                        j.getFinishedAt(),
                        nz(j.getError()),
                        nz(j.getApkUrl()),
                        nz(j.getBundleUrl()),
                        nz(j.getIpaUrl())
                ))
                .toList();
    }

    @GetMapping(value = "/{linkId}/build-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId
    ) {
        requireOwnerLinkAccess(authHeader, linkId);

        var lastAndroid = jobRepo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, BuildPlatform.ANDROID).orElse(null);
        var lastIos = jobRepo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, BuildPlatform.IOS).orElse(null);

        Map<String, Object> body = new HashMap<>();
        body.put("linkId", linkId);

        body.put("android", lastAndroid == null ? null : Map.of(
                "status", lastAndroid.getStatus() == null ? null : lastAndroid.getStatus().name(),
                "buildId", nz(lastAndroid.getCiBuildId()),
                "apkUrl", nz(lastAndroid.getApkUrl()),
                "aabUrl", nz(lastAndroid.getBundleUrl()),
                "requestedAt", lastAndroid.getCreatedAt(),
                "startedAt", lastAndroid.getStartedAt(),
                "finishedAt", lastAndroid.getFinishedAt(),
                "error", nz(lastAndroid.getError())
        ));

        body.put("ios", lastIos == null ? null : Map.of(
                "status", lastIos.getStatus() == null ? null : lastIos.getStatus().name(),
                "buildId", nz(lastIos.getCiBuildId()),
                "ipaUrl", nz(lastIos.getIpaUrl()),
                "requestedAt", lastIos.getCreatedAt(),
                "startedAt", lastIos.getStartedAt(),
                "finishedAt", lastIos.getFinishedAt(),
                "error", nz(lastIos.getError())
        ));

        return ResponseEntity.ok(body);
    }
    
    
    @GetMapping(value = "/{linkId}/build-jobs/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> latest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(required = false) String platform
    ) {
        requireOwnerLinkAccess(authHeader, linkId);

        BuildPlatform p = null;
        if (platform != null && !platform.isBlank()) {
            try {
                p = BuildPlatform.valueOf(platform.trim().toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid platform. Use ANDROID or IOS"));
            }
        }

        var job = (p == null)
                ? jobRepo.findTop1ByApp_IdOrderByCreatedAtDesc(linkId).orElse(null) // (need repo method)
                : jobRepo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, p).orElse(null);

        if (job == null) {
            return ResponseEntity.ok(Map.of("message", "No build jobs found", "linkId", linkId));
        }

        return ResponseEntity.ok(new AppBuildJobDto(
                job.getId(),
                job.getApp() != null ? job.getApp().getId() : null,
                job.getPlatform() != null ? job.getPlatform().name() : null,
                job.getStatus() != null ? job.getStatus().name() : null,
                nz(job.getCiBuildId()),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                nz(job.getError()),
                nz(job.getApkUrl()),
                nz(job.getBundleUrl()),
                nz(job.getIpaUrl())
        ));
    }


    // ---------- auth helpers ----------
    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.replace("Bearer ", "").trim();
    }

    private void requireOwnerLinkAccess(String authHeader, Long linkId) {
        String token = extractToken(authHeader);
        Long tokenAdminId = jwtUtil.extractAdminId(token);

        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));

        Long linkOwnerId = (link.getAdmin() != null) ? link.getAdmin().getAdminId() : null;
        if (linkOwnerId == null || !linkOwnerId.equals(tokenAdminId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: link does not belong to this owner");
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
