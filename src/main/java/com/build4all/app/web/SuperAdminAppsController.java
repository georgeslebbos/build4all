package com.build4all.app.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.dto.SuperAdminAppDetailsDto;
import com.build4all.app.dto.SuperAdminAppRowDto;
import com.build4all.app.service.AppRequestService;
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
@RequestMapping("/api/super-admin/apps")
public class SuperAdminAppsController {

    private final AdminUserProjectRepository aupRepo;
    private final AppRequestService service;
    private final JwtUtil jwtUtil;

    public SuperAdminAppsController(
            AdminUserProjectRepository aupRepo,
            AppRequestService service,
            JwtUtil jwtUtil
    ) {
        this.aupRepo = aupRepo;
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    // ✅ NO ENTITIES RETURNED (no lazy crashes)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SuperAdminAppRowDto> listAll(@RequestHeader("Authorization") String authHeader) {
        requireSuperAdmin(authHeader);
        return aupRepo.findAllForSuperAdmin();
    }

    @GetMapping(value = "/{linkId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SuperAdminAppDetailsDto getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId
    ) {
        requireSuperAdmin(authHeader);
        return aupRepo.findDetailsForSuperAdmin(linkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found"));
    }

    // ---------------- REBUILD ANDROID (AAB) ----------------

    @PostMapping(value = "/{linkId}/rebuild-bundle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildAndroidBundle(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpVersion,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        requireSuperAdmin(authHeader);

        var link = service.rebuildAndroidBundleSamePackage(
                linkId,
                bumpVersion,
                apiBaseUrlOverride,
                navJson,
                homeJson,
                enabledFeaturesJson,
                brandingJson
        );

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Android bundle rebuild started (SUPER_ADMIN)");
        body.put("ownerProjectLinkId", link.getId());
        body.put("appName", nz(link.getAppName()));
        body.put("slug", nz(link.getSlug()));
        body.put("androidPackageName", nz(link.getAndroidPackageName()));
        body.put("androidVersionCode", link.getAndroidVersionCode());
        body.put("androidVersionName", nz(link.getAndroidVersionName()));
        body.put("bundleUrl", nz(link.getBundleUrl()));
        body.put("apkUrl", nz(link.getApkUrl()));
        return ResponseEntity.ok(body);
    }

    // ---------------- REBUILD iOS (IPA) ----------------

    @PostMapping(value = "/{linkId}/rebuild-ios", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildIosIpa(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long linkId,
            @RequestParam(defaultValue = "true") boolean bumpVersion,
            @RequestParam(required = false) String apiBaseUrlOverride,
            @RequestParam(required = false) String navJson,
            @RequestParam(required = false) String homeJson,
            @RequestParam(required = false) String enabledFeaturesJson,
            @RequestParam(required = false) String brandingJson
    ) {
        requireSuperAdmin(authHeader);

        OwnerIdentity owner = resolveOwnerIdentityOrThrow(linkId);

        var link = service.rebuildIosIpaSameBundle(
                linkId,
                bumpVersion,
                apiBaseUrlOverride,
                navJson,
                homeJson,
                enabledFeaturesJson,
                brandingJson,
                owner.email,
                owner.name
        );

        Map<String, Object> body = new HashMap<>();
        body.put("message", "iOS IPA rebuild started (SUPER_ADMIN)");
        body.put("ownerProjectLinkId", link.getId());
        body.put("appName", nz(link.getAppName()));
        body.put("slug", nz(link.getSlug()));
        body.put("iosBundleId", nz(link.getIosBundleId()));
        body.put("iosBuildNumber", link.getIosBuildNumber());
        body.put("iosVersionName", nz(link.getIosVersionName()));
        body.put("ipaUrl", nz(link.getIpaUrl()));
        return ResponseEntity.ok(body);
    }

    // ---------------- REBUILD BOTH ----------------

    @PostMapping(value = "/{linkId}/rebuild-both", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rebuildBoth(
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
        requireSuperAdmin(authHeader);

        OwnerIdentity owner = resolveOwnerIdentityOrThrow(linkId);

        var link = service.rebuildAndroidAndIos(
                linkId,
                bumpAndroid,
                bumpIos,
                apiBaseUrlOverride,
                navJson,
                homeJson,
                enabledFeaturesJson,
                brandingJson,
                owner.email,
                owner.name
        );

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Android + iOS rebuild started (SUPER_ADMIN)");
        body.put("ownerProjectLinkId", link.getId());
        body.put("appName", nz(link.getAppName()));
        body.put("slug", nz(link.getSlug()));

        body.put("androidPackageName", nz(link.getAndroidPackageName()));
        body.put("androidVersionCode", link.getAndroidVersionCode());
        body.put("androidVersionName", nz(link.getAndroidVersionName()));
        body.put("bundleUrl", nz(link.getBundleUrl()));

        body.put("iosBundleId", nz(link.getIosBundleId()));
        body.put("iosBuildNumber", link.getIosBuildNumber());
        body.put("iosVersionName", nz(link.getIosVersionName()));
        body.put("ipaUrl", nz(link.getIpaUrl()));

        return ResponseEntity.ok(body);
    }

    // ---------------- OWNER IDENTITY (NO LAZY ENTITIES) ----------------

    private OwnerIdentity resolveOwnerIdentityOrThrow(Long linkId) {
        if (!aupRepo.existsById(linkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found");
        }

        String ownerEmail = nz(aupRepo.findOwnerUsernameByLinkId(linkId).orElse("")).trim();
        String ownerName = nz(aupRepo.findOwnerNameByLinkId(linkId).orElse("")).trim();

        if (ownerName.isBlank()) ownerName = ownerEmail.isBlank() ? "Owner" : ownerEmail;
        if (ownerEmail.isBlank()) ownerEmail = "owner@unknown";

        return new OwnerIdentity(ownerEmail, ownerName);
    }

    private static class OwnerIdentity {
        final String email;
        final String name;
        OwnerIdentity(String email, String name) { this.email = email; this.name = name; }
    }

    // ---------------- AUTH HELPERS ----------------

    private void requireSuperAdmin(String authHeader) {
        String token = extractToken(authHeader);
        String role = jwtUtil.extractRole(token);
        if (!"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: SUPER_ADMIN only");
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.replace("Bearer ", "").trim();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
