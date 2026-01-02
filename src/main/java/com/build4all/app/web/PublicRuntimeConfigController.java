package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.repository.ThemeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicRuntimeConfigController {

    private final AdminUserProjectRepository linkRepo;
    private final AppRuntimeConfigRepository runtimeRepo;
    private final ThemeRepository themeRepo;

    @Value("${ci.runtime-token:}")
    private String ciRuntimeToken;

    // ✅ TEMP switch: if true -> allow no-token access for by-link
    @Value("${ci.runtime-token-disabled:false}")
    private boolean ciRuntimeTokenDisabled;

    public PublicRuntimeConfigController(AdminUserProjectRepository linkRepo,
                                         AppRuntimeConfigRepository runtimeRepo,
                                         ThemeRepository themeRepo) {
        this.linkRepo = linkRepo;
        this.runtimeRepo = runtimeRepo;
        this.themeRepo = themeRepo;
    }

    // quick sanity check
    @GetMapping("/runtime-config/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "ok", true,
                "tokenDisabled", ciRuntimeTokenDisabled,
                "tokenLoaded", ciRuntimeToken != null && !ciRuntimeToken.trim().isBlank()
        );
    }

    @GetMapping("/runtime-config")
    public ResponseEntity<Map<String, Object>> runtimeConfig(@RequestParam Long ownerId,
                                                             @RequestParam Long projectId,
                                                             @RequestParam String slug) {

        String s = slugify(slug);

        AdminUserProject link = linkRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, s)
                .orElseThrow(() -> new IllegalArgumentException("App not found"));

        return ResponseEntity.ok(buildResponse(link));
    }

    /**
     * ✅ CI endpoint:
     * /api/public/runtime-config/by-link?linkId=14
     *
     * TEMP MODE:
     * - if ci.runtime-token-disabled=true -> NO TOKEN REQUIRED
     * - else -> require X-Auth-Token
     */
    @Transactional(readOnly = true)
    @GetMapping("/runtime-config/by-link")
    public ResponseEntity<Map<String, Object>> runtimeConfigByLink(
            @RequestParam Long linkId,
            @RequestHeader(value = "X-Auth-Token", required = false) String xAuthToken
    ) {
        if (!ciRuntimeTokenDisabled) {
            if (!validCiToken(xAuthToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unauthorized - missing or invalid token"));
            }
        } else {
            System.out.println("⚠️ CI TOKEN CHECK DISABLED (TEST MODE) for /by-link");
        }

        AdminUserProject link = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App not found"));

        return ResponseEntity.ok(buildResponse(link));
    }

    // ---------------- core builder ----------------

    private Map<String, Object> buildResponse(AdminUserProject link) {
        AppRuntimeConfig cfg = runtimeRepo.findByApp_Id(link.getId()).orElse(null);

        String nav = cfg != null ? nz(cfg.getNavJson()) : "[]";
        String home = cfg != null ? nz(cfg.getHomeJson()) : "{}";
        String features = cfg != null ? nz(cfg.getEnabledFeaturesJson()) : "[]";
        String branding = cfg != null ? nz(cfg.getBrandingJson()) : "{}";

        String themeJson = resolveThemeJson(link.getThemeId());

        Map<String, Object> res = new HashMap<>();

        res.put("OWNER_ID", link.getAdmin().getAdminId().toString());
        res.put("PROJECT_ID", link.getProject().getId().toString());
        res.put("SLUG", link.getSlug());
        res.put("OWNER_PROJECT_LINK_ID", String.valueOf(link.getId()));

        res.put("APP_NAME", nz(link.getAppName()));
        res.put("STATUS", nz(link.getStatus()));
        res.put("LICENSE_ID", nz(link.getLicenseId()));

        res.put("APP_TYPE", link.getProject() != null && link.getProject().getProjectType() != null
                ? link.getProject().getProjectType().name()
                : null);

        res.put("THEME_ID", link.getThemeId());
        res.put("THEME_JSON", themeJson);
        res.put("THEME_JSON_B64", b64(themeJson));

        res.put("CURRENCY_CODE", link.getCurrency() != null ? link.getCurrency().getCode() : null);
        res.put("CURRENCY_SYMBOL", link.getCurrency() != null ? link.getCurrency().getSymbol() : null);

        res.put("LOGO_URL", nz(link.getLogoUrl()));
        res.put("APK_URL", nz(link.getApkUrl()));
        res.put("IPA_URL", nz(link.getIpaUrl()));
        res.put("BUNDLE_URL", nz(link.getBundleUrl()));

        res.put("API_BASE_URL_OVERRIDE", cfg != null ? cfg.getApiBaseUrlOverride() : null);

        res.put("NAV_JSON", nav);
        res.put("HOME_JSON", home);
        res.put("ENABLED_FEATURES_JSON", features);
        res.put("BRANDING_JSON", branding);

        res.put("NAV_JSON_B64", b64(nav));
        res.put("HOME_JSON_B64", b64(home));
        res.put("ENABLED_FEATURES_JSON_B64", b64(features));
        res.put("BRANDING_JSON_B64", b64(branding));

        return res;
    }

    // ---------------- helpers ----------------

    private boolean validCiToken(String token) {
        String expected = (ciRuntimeToken == null) ? "" : ciRuntimeToken.trim();
        String got = (token == null) ? "" : token.trim();

        System.out.println(">>> CI expected len=" + expected.length() + " | got len=" + got.length());

        if (expected.isBlank()) return false;
        return expected.equals(got);
    }

    private String resolveThemeJson(Long themeId) {
        Theme theme = null;

        if (themeId != null) {
            theme = themeRepo.findById(themeId).orElse(null);
        }
        if (theme == null) {
            theme = themeRepo.findByIsActiveTrue().orElse(null);
        }

        if (theme == null || theme.getThemeJson() == null || theme.getThemeJson().isBlank()) {
            return "{}";
        }
        return theme.getThemeJson();
    }

    private static String b64(String s) {
        if (s == null) s = "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
