package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.repository.ThemeRepository;
import org.springframework.http.ResponseEntity;
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

    public PublicRuntimeConfigController(AdminUserProjectRepository linkRepo,
                                         AppRuntimeConfigRepository runtimeRepo,
                                         ThemeRepository themeRepo) {
        this.linkRepo = linkRepo;
        this.runtimeRepo = runtimeRepo;
        this.themeRepo = themeRepo;
    }

    /**
     * Endpoint for GitHub Actions / Builder:
     * /api/public/runtime-config?ownerId=1&projectId=1&slug=my-app
     */
    @GetMapping("/runtime-config")
    public ResponseEntity<Map<String, Object>> runtimeConfig(@RequestParam Long ownerId,
                                                             @RequestParam Long projectId,
                                                             @RequestParam String slug) {

        String s = slugify(slug);

        AdminUserProject link = linkRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, s)
                .orElseThrow(() -> new IllegalArgumentException("App not found"));

        AppRuntimeConfig cfg = runtimeRepo.findByApp_Id(link.getId()).orElse(null);

        // Defaults
        String nav = cfg != null ? nz(cfg.getNavJson()) : "[]";
        String home = cfg != null ? nz(cfg.getHomeJson()) : "{}";
        String features = cfg != null ? nz(cfg.getEnabledFeaturesJson()) : "[]";
        String branding = cfg != null ? nz(cfg.getBrandingJson()) : "{}";

        // Resolve theme json (themeId -> theme, else active theme, else "{}")
        String themeJson = resolveThemeJson(link.getThemeId());

        Map<String, Object> res = new HashMap<>();

        // Identity
        res.put("OWNER_ID", ownerId.toString());
        res.put("PROJECT_ID", projectId.toString());
        res.put("SLUG", link.getSlug());
        res.put("OWNER_PROJECT_LINK_ID", String.valueOf(link.getId()));

        // App meta
        res.put("APP_NAME", nz(link.getAppName()));
        res.put("STATUS", nz(link.getStatus()));
        res.put("LICENSE_ID", nz(link.getLicenseId()));

        // ✅ App type (from Project)
        res.put("APP_TYPE", link.getProject() != null && link.getProject().getProjectType() != null
                ? link.getProject().getProjectType().name()
                : null);

        // Theme + currency
        res.put("THEME_ID", link.getThemeId());
        res.put("THEME_JSON", themeJson);
        res.put("THEME_JSON_B64", b64(themeJson));

        res.put("CURRENCY_CODE", link.getCurrency() != null ? link.getCurrency().getCode() : null);
        res.put("CURRENCY_SYMBOL", link.getCurrency() != null ? link.getCurrency().getSymbol() : null);

        // Branding/logo + artifacts (useful for dashboard too)
        res.put("LOGO_URL", nz(link.getLogoUrl()));
        res.put("APK_URL", nz(link.getApkUrl()));
        res.put("IPA_URL", nz(link.getIpaUrl()));
        res.put("BUNDLE_URL", nz(link.getBundleUrl()));

        // API override (optional)
        res.put("API_BASE_URL_OVERRIDE", cfg != null ? cfg.getApiBaseUrlOverride() : null);

        // Raw JSON (debug-friendly)
        res.put("NAV_JSON", nav);
        res.put("HOME_JSON", home);
        res.put("ENABLED_FEATURES_JSON", features);
        res.put("BRANDING_JSON", branding);

        // Base64 (action/env-friendly)
        res.put("NAV_JSON_B64", b64(nav));
        res.put("HOME_JSON_B64", b64(home));
        res.put("ENABLED_FEATURES_JSON_B64", b64(features));
        res.put("BRANDING_JSON_B64", b64(branding));

        return ResponseEntity.ok(res);
    }

    // ---------------- helpers ----------------

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
