package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.app.domain.AppBuildJob;
import com.build4all.app.domain.AppRequest;
import com.build4all.app.domain.BuildPlatform;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.app.repository.AppBuildJobRepository;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.app.service.ThemeJsonBuilder;
import com.build4all.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner")
public class OwnerAppRequestController {

    private final AppRequestService service;
    private final AppRequestRepository appRequestRepo;
    private final AdminUserProjectRepository aupRepo;
    private final JwtUtil jwtUtil;
    private final AdminUsersRepository adminRepo;
    private final AppBuildJobRepository buildJobRepo;

    @Value("${ci.callbackUrl:}")
    private String callbackBase;

    public OwnerAppRequestController(
            AppRequestService service,
            AppRequestRepository appRequestRepo,
            AdminUserProjectRepository aupRepo,
            JwtUtil jwtUtil,
            AdminUsersRepository adminRepo,
            AppBuildJobRepository buildJobRepo
    ) {
        this.service = service;
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
        this.jwtUtil = jwtUtil;
        this.adminRepo = adminRepo;
        this.buildJobRepo = buildJobRepo;
    }

  

    // ------------------------------------------------------------------
    // Legacy JSON create (kept)
    // ------------------------------------------------------------------
    @PostMapping(
            value = "/app-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId,
            @RequestBody CreateAppRequestDto dto
    ) {
        try {
            requireOwnerId(authHeader, ownerId);

            AppRequest r = service.createRequest(
                    ownerId,
                    dto.projectId(),
                    dto.appName(),
                    dto.slug(),
                    dto.logoUrl(),
                    dto.themeId(),
                    dto.themeJson(),
                    dto.notes()
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Request created");
            body.put("requestId", r.getId());
            body.put("status", r.getStatus());
            body.put("appName", nz(r.getAppName()));
            body.put("slug", nz(r.getSlug()));
            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    // ✅ AUTO flow (Android)
    @PostMapping(
            value = "/app-requests/auto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAndAutoApprove(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId,
            @RequestParam Long projectId,
            @RequestParam String appName,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(required = false) String primaryColor,
            @RequestParam(required = false) String secondaryColor,
            @RequestParam(required = false) String backgroundColor,
            @RequestParam(required = false) String onBackgroundColor,
            @RequestParam(required = false) String errorColor,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson,
            @RequestParam(required = false) String apiBaseUrlOverride
    ) {
        try {
            requireOwnerId(authHeader, ownerId);

            MultipartFile logoFile = (file != null) ? file : logo;

            String themeJson = ThemeJsonBuilder.buildThemeJson(
                    primaryColor,
                    secondaryColor,
                    backgroundColor,
                    onBackgroundColor,
                    errorColor
            );

            AdminUserProject link = service.createAndAutoApprove(
                    ownerId,
                    projectId,
                    appName,
                    slug,
                    logoFile,
                    themeId,
                    notes,
                    themeJson,
                    currencyId,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    apiBaseUrlOverride
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Android build started");
            body.put("adminId", ownerId);
            body.put("projectId", projectId);
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("status", nz(link.getStatus()));
            body.put("licenseId", nz(link.getLicenseId()));
            body.put("themeId", link.getThemeId());
            body.put("validFrom", link.getValidFrom());
            body.put("endTo", link.getEndTo());
            body.put("logoUrl", nz(link.getLogoUrl()));
            body.put("apkUrl", nz(link.getApkUrl()));
            body.put("bundleUrl", nz(link.getBundleUrl()));
            body.put("themeJson", themeJson);
            body.put("currencyId", currencyId);

            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("androidPackageName", nz(link.getAndroidPackageName()));

            String manifestUrlGuess =
                    "https://raw.githubusercontent.com/fatimahh0/HobbySphereFlutter/main/builds/"
                            + ownerId + "/" + projectId + "/" + link.getSlug() + "/latest.json";
            body.put("manifestUrlHint", manifestUrlGuess);

            body.put("callbackBase", nz(callbackBase));

            body.put("runtimeConfigUrl",
                    "/api/public/runtime-config?ownerId=" + ownerId
                            + "&projectId=" + projectId
                            + "&slug=" + link.getSlug()
            );

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    // ✅ AUTO flow (iOS)
    @PostMapping(
            value = "/app-requests/auto/ios",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAndAutoApproveIos(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId,
            @RequestParam Long projectId,
            @RequestParam String appName,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(required = false) String primaryColor,
            @RequestParam(required = false) String secondaryColor,
            @RequestParam(required = false) String backgroundColor,
            @RequestParam(required = false) String onBackgroundColor,
            @RequestParam(required = false) String errorColor,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson,
            @RequestParam(required = false) String apiBaseUrlOverride
    ) {
        try {
            requireOwnerId(authHeader, ownerId);

            String token = extractToken(authHeader);
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName = extractOwnerNameFromToken(token);

            MultipartFile logoFile = (file != null) ? file : logo;

            String themeJson = ThemeJsonBuilder.buildThemeJson(
                    primaryColor,
                    secondaryColor,
                    backgroundColor,
                    onBackgroundColor,
                    errorColor
            );

            AdminUserProject link = service.createAndAutoApproveIos(
                    ownerId,
                    projectId,
                    appName,
                    slug,
                    logoFile,
                    themeId,
                    notes,
                    themeJson,
                    currencyId,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    apiBaseUrlOverride,
                    ownerEmail,
                    ownerName
            );

            link = aupRepo.findById(link.getId()).orElse(link);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS build started");
            body.put("adminId", ownerId);
            body.put("projectId", projectId);
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("status", nz(link.getStatus()));
            body.put("licenseId", nz(link.getLicenseId()));
            body.put("themeId", link.getThemeId());
            body.put("validFrom", link.getValidFrom());
            body.put("endTo", link.getEndTo());
            body.put("logoUrl", nz(link.getLogoUrl()));
            body.put("ipaUrl", nz(link.getIpaUrl()));
            body.put("themeJson", themeJson);
            body.put("currencyId", currencyId);

            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));
            body.put("iosBundleId", nz(link.getIosBundleId()));

            body.put("callbackBase", nz(callbackBase));

            body.put("runtimeConfigUrl",
                    "/api/public/runtime-config?ownerId=" + ownerId
                            + "&projectId=" + projectId
                            + "&slug=" + link.getSlug()
            );

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    // ✅ AUTO flow (Android + iOS)
    @PostMapping(
            value = "/app-requests/auto/both",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAndAutoApproveBoth(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId,
            @RequestParam Long projectId,
            @RequestParam String appName,
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) Long themeId,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(required = false) String primaryColor,
            @RequestParam(required = false) String secondaryColor,
            @RequestParam(required = false) String backgroundColor,
            @RequestParam(required = false) String onBackgroundColor,
            @RequestParam(required = false) String errorColor,
            @RequestParam(required = false) Long currencyId,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson,
            @RequestParam(required = false) String apiBaseUrlOverride
    ) {
        try {
            requireOwnerId(authHeader, ownerId);

            String token = extractToken(authHeader);
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName = extractOwnerNameFromToken(token);

            MultipartFile logoFile = (file != null) ? file : logo;

            String themeJson = ThemeJsonBuilder.buildThemeJson(
                    primaryColor,
                    secondaryColor,
                    backgroundColor,
                    onBackgroundColor,
                    errorColor
            );

            AdminUserProject link = service.createAndAutoApproveBoth(
                    ownerId,
                    projectId,
                    appName,
                    slug,
                    logoFile,
                    themeId,
                    notes,
                    themeJson,
                    currencyId,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    apiBaseUrlOverride,
                    ownerEmail,
                    ownerName
            );

            link = aupRepo.findById(link.getId()).orElse(link);

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Android + iOS builds started");

            body.put("adminId", ownerId);
            body.put("projectId", projectId);
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("status", nz(link.getStatus()));
            body.put("licenseId", nz(link.getLicenseId()));
            body.put("themeId", link.getThemeId());
            body.put("validFrom", link.getValidFrom());
            body.put("endTo", link.getEndTo());
            body.put("logoUrl", nz(link.getLogoUrl()));
            body.put("themeJson", themeJson);
            body.put("currencyId", currencyId);

            body.put("apkUrl", nz(link.getApkUrl()));
            body.put("bundleUrl", nz(link.getBundleUrl()));
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("androidPackageName", nz(link.getAndroidPackageName()));

            body.put("ipaUrl", nz(link.getIpaUrl()));
            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));
            body.put("iosBundleId", nz(link.getIosBundleId()));

            String manifestUrlGuess =
                    "https://raw.githubusercontent.com/fatimahh0/HobbySphereFlutter/main/builds/"
                            + ownerId + "/" + projectId + "/" + link.getSlug() + "/latest.json";
            body.put("manifestUrlHint", manifestUrlGuess);

            body.put("callbackBase", nz(callbackBase));

            body.put("runtimeConfigUrl",
                    "/api/public/runtime-config?ownerId=" + ownerId
                            + "&projectId=" + projectId
                            + "&slug=" + link.getSlug()
            );

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    // ✅ rebuild only Android bundle
    @PostMapping(value = "/apps/{linkId}/rebuild-bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildBundleSamePackage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpVersion,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        try {
            requireOwnerLinkAccess(authHeader, linkId);

            AdminUserProject link = service.rebuildAndroidBundleSamePackage(
                    linkId,
                    bumpVersion,
                    apiBaseUrlOverride,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Bundle rebuild started");
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));
            body.put("androidPackageName", nz(link.getAndroidPackageName()));
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("bundleUrl", nz(link.getBundleUrl()));
            body.put("apkUrl", nz(link.getApkUrl()));
            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }
    
 // ✅ UNIVERSAL rebuild endpoint (Android + iOS)
    @PostMapping(value = "/apps/{linkId}/rebuild", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildUniversal(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpAndroid,
            @RequestParam(defaultValue = "true") boolean bumpIos
    ) {
        try {
            String token = extractToken(authHeader);
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName = extractOwnerNameFromToken(token);

            requireOwnerLinkAccess(authHeader, linkId);

            AdminUserProject link = service.rebuildAndroidAndIos(
                    linkId,
                    bumpAndroid,
                    bumpIos,
                    null, null, null, null, null,
                    ownerEmail,
                    ownerName
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Rebuild started");
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));

            body.put("androidPackageName", nz(link.getAndroidPackageName()));
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));

            body.put("iosBundleId", nz(link.getIosBundleId()));
            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }


    // ✅ rebuild only iOS IPA
    @PostMapping(value = "/apps/{linkId}/rebuild-ios", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildIosIpaSameBundle(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpVersion,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        try {
            String token = extractToken(authHeader);
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName = extractOwnerNameFromToken(token);

            requireOwnerLinkAccess(authHeader, linkId);

            AdminUserProject link = service.rebuildIosIpaSameBundle(
                    linkId,
                    bumpVersion,
                    apiBaseUrlOverride,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    ownerEmail,
                    ownerName
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "iOS IPA rebuild started");
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));

            body.put("iosBundleId", nz(link.getIosBundleId()));
            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));
            body.put("ipaUrl", nz(link.getIpaUrl()));

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    // ✅ rebuild both
    @PostMapping(value = "/apps/{linkId}/rebuild-both", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildAndroidAndIos(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpAndroid,
            @RequestParam(defaultValue = "true") boolean bumpIos,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        try {
            String token = extractToken(authHeader);
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName = extractOwnerNameFromToken(token);

            requireOwnerLinkAccess(authHeader, linkId);

            AdminUserProject link = service.rebuildAndroidAndIos(
                    linkId,
                    bumpAndroid,
                    bumpIos,
                    apiBaseUrlOverride,
                    navJson,
                    homeJson,
                    enabledFeaturesJson,
                    brandingJson,
                    ownerEmail,
                    ownerName
            );

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Android + iOS rebuild started");
            body.put("ownerProjectLinkId", link.getId());
            body.put("slug", nz(link.getSlug()));
            body.put("appName", nz(link.getAppName()));

            body.put("androidPackageName", nz(link.getAndroidPackageName()));
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("bundleUrl", nz(link.getBundleUrl()));

            body.put("iosBundleId", nz(link.getIosBundleId()));
            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));
            body.put("ipaUrl", nz(link.getIpaUrl()));

            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping(value = "/app-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AppRequest> myRequests(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId
    ) {
        requireOwnerId(authHeader, ownerId);
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping(value = "/my-apps", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OwnerProjectView> myApps(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam Long ownerId
    ) {
        requireOwnerId(authHeader, ownerId);
        return aupRepo.findOwnerProjectsSlim(ownerId);
    }

    // ---------------- helpers ----------------

    private Map<String, Object> toJobMap(AppBuildJob j) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("platform", j.getPlatform() == null ? "" : j.getPlatform().name());
        m.put("status", j.getStatus() == null ? "" : j.getStatus().name());
        m.put("ciBuildId", nz(j.getCiBuildId()));

        // Android meta
        m.put("androidVersionCode", j.getAndroidVersionCode());
        m.put("androidVersionName", nz(j.getAndroidVersionName()));
        m.put("androidPackageName", nz(j.getAndroidPackageName()));

        // iOS meta
        m.put("iosBuildNumber", j.getIosBuildNumber());
        m.put("iosVersionName", nz(j.getIosVersionName()));
        m.put("iosBundleId", nz(j.getIosBundleId()));

        // Artifacts
        m.put("apkUrl", nz(j.getApkUrl()));
        m.put("bundleUrl", nz(j.getBundleUrl()));
        m.put("ipaUrl", nz(j.getIpaUrl()));

        m.put("error", nz(j.getError()));

        // Timestamps
        m.put("createdAt", j.getCreatedAt());
        m.put("startedAt", j.getStartedAt());
        m.put("finishedAt", j.getFinishedAt());
        m.put("updatedAt", j.getUpdatedAt());

        
        return m;
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }

    private String extractOwnerNameFromToken(String token) {
        try {
            Long adminId = jwtUtil.extractAdminId(token);
            return adminRepo.findById(adminId)
                    .map(admin -> {
                        String first = admin.getFirstName() != null ? admin.getFirstName() : "";
                        String last = admin.getLastName() != null ? admin.getLastName() : "";
                        String full = (first + " " + last).trim();
                        return full.isBlank() ? admin.getUsername() : full;
                    })
                    .orElse("Owner");
        } catch (Exception e) {
            return "Owner";
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.replace("Bearer ", "").trim();
    }

    private Long requireOwnerId(String authHeader, Long ownerIdParam) {
        String token = extractToken(authHeader);
        Long tokenAdminId = jwtUtil.extractAdminId(token);

        if (ownerIdParam == null || !ownerIdParam.equals(tokenAdminId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Forbidden: ownerId does not match token"
            );
        }
        return tokenAdminId;
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
}
