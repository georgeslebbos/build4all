package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.OwnerProjectView;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.app.domain.AppRequest;
import com.build4all.app.dto.CreateAppRequestDto;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.service.AppRequestService;
import com.build4all.app.service.ThemeJsonBuilder;
import com.build4all.security.JwtUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Value("${ci.callbackUrl:}")
    private String callbackBase;

    public OwnerAppRequestController(AppRequestService service,
            AppRequestRepository appRequestRepo,
            AdminUserProjectRepository aupRepo,
            JwtUtil jwtUtil,
            AdminUsersRepository adminRepo) {
this.service = service;
this.appRequestRepo = appRequestRepo;
this.aupRepo = aupRepo;
this.jwtUtil = jwtUtil;
this.adminRepo = adminRepo;
}
    // Legacy JSON create (kept)
    @PostMapping(
            value = "/app-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> create(@RequestParam Long ownerId,
                                    @RequestBody CreateAppRequestDto dto) {
        try {
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
            body.put("bundleUrl", nz(link.getBundleUrl())); // if present later
            body.put("themeJson", themeJson);
            body.put("currencyId", currencyId);

            // ✅ Version + package (very useful for debugging + UI)
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("androidPackageName", nz(link.getAndroidPackageName()));

            String manifestUrlGuess =
                    "https://raw.githubusercontent.com/fatimahh0/HobbySphereFlutter/main/builds/"
                            + ownerId + "/" + projectId + "/" + link.getSlug() + "/latest.json";
            body.put("manifestUrlHint", manifestUrlGuess);

            // ✅ Keep callbackBase only (token must NEVER be returned)
            body.put("callbackBase", nz(callbackBase));

            body.put("runtimeConfigUrl",
                    "/api/public/runtime-config?ownerId=" + ownerId
                            + "&projectId=" + projectId
                            + "&slug=" + link.getSlug()
            );

            return ResponseEntity.ok(body);

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
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestHeader("Authorization") String authHeader

    ) {
        try {
        	String token = authHeader.replace("Bearer ", "").trim();
        	String ownerEmail = jwtUtil.extractUsername(token); // subject = email (OWNER token)
        	String ownerName  = extractOwnerNameFromToken(token);

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

            // ✅ IMPORTANT: expose iOS version + bundle id
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

        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }
    
    @PostMapping(
            value = "/app-requests/auto/both",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAndAutoApproveBoth(
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
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestHeader("Authorization") String authHeader

    ) {
        try {
        	
        	String token = authHeader.replace("Bearer ", "").trim();
        	String ownerEmail = jwtUtil.extractUsername(token);
        	String ownerName  = extractOwnerNameFromToken(token);
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

            Map<String, Object> body = new HashMap<>();
            body.put("message", "Android + iOS builds started");

            // common
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

            // Android build info
            body.put("apkUrl", nz(link.getApkUrl()));
            body.put("bundleUrl", nz(link.getBundleUrl()));
            body.put("androidVersionCode", link.getAndroidVersionCode());
            body.put("androidVersionName", nz(link.getAndroidVersionName()));
            body.put("androidPackageName", nz(link.getAndroidPackageName()));

            // iOS build info
            body.put("ipaUrl", nz(link.getIpaUrl()));
            body.put("iosBuildNumber", link.getIosBuildNumber());
            body.put("iosVersionName", nz(link.getIosVersionName()));
            body.put("iosBundleId", nz(link.getIosBundleId()));

            // Optional manifest hint (Android)
            String manifestUrlGuess =
                    "https://raw.githubusercontent.com/fatimahh0/HobbySphereFlutter/main/builds/"
                            + ownerId + "/" + projectId + "/" + link.getSlug() + "/latest.json";
            body.put("manifestUrlHint", manifestUrlGuess);

            // ✅ Keep callbackBase only (never return token)
            body.put("callbackBase", nz(callbackBase));

            body.put("runtimeConfigUrl",
                    "/api/public/runtime-config?ownerId=" + ownerId
                            + "&projectId=" + projectId
                            + "&slug=" + link.getSlug()
            );

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    @PostMapping(
            value = "/apps/{linkId}/rebuild-bundle",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> rebuildBundleSamePackage(
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpVersion,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        try {
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
            body.put("bundleUrl", nz(link.getBundleUrl())); // will be "" because you reset it
            body.put("apkUrl", nz(link.getApkUrl())); // unchanged
            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal error",
                    "details", ex.getClass().getSimpleName()
            ));
        }
    }

    @PostMapping(
            value = "/apps/{linkId}/rebuild-both",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> rebuildAndroidAndIos(
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpAndroid,
            @RequestParam(defaultValue = "true") boolean bumpIos,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            String token = authHeader.replace("Bearer ", "").trim();
            String ownerEmail = jwtUtil.extractUsername(token);
            String ownerName  = extractOwnerNameFromToken(token);

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
    public List<AppRequest> myRequests(@RequestParam Long ownerId) {
        return appRequestRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @GetMapping(value = "/my-apps", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<OwnerProjectView> myApps(@RequestParam Long ownerId) {
        return aupRepo.findOwnerProjectsSlim(ownerId);
    }

    private static String nz(String s) { return (s == null) ? "" : s; }
    
    private String extractOwnerNameFromToken(String token) {
        try {
            Long adminId = jwtUtil.extractAdminId(token);
            return adminRepo.findById(adminId)
                .map(admin -> {
                    String first = admin.getFirstName() != null ? admin.getFirstName() : "";
                    String last  = admin.getLastName() != null ? admin.getLastName() : "";
                    String full = (first + " " + last).trim();
                    return full.isBlank() ? admin.getUsername() : full;
                })
                .orElse("Owner");
        } catch (Exception e) {
            return "Owner";
        }
    }

    
    
}
