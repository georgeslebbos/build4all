package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.repository.ThemeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicRuntimeConfigController {

    private final AdminUserProjectRepository linkRepo;
    private final AppRuntimeConfigRepository runtimeRepo;
    private final ThemeRepository themeRepo;

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        // ✅ Resolve theme JSON from DB, then normalize it to Flutter schema
        String themeJsonRaw = resolveThemeJson(link.getThemeId());
        String themeJson = normalizeThemeJsonToMobileSchema(themeJsonRaw);

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

        // ✅ return normalized json + b64 of normalized json
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

    // ---------------- theme normalization (THE FIX) ----------------

    /**
     * Ensures the returned theme JSON matches what Flutter expects:
     * {
     *   "menuType": "...",
     *   "valuesMobile": {
     *     "colors": { "primary": "...", ... },
     *     "card": {...},
     *     "search": {...},
     *     "button": {...}
     *   }
     * }
     *
     * If DB contains old flat theme like {"primary":"#.."} we wrap it.
     */
    @SuppressWarnings("unchecked")
    private String normalizeThemeJsonToMobileSchema(String themeJsonRaw) {
        try {
            if (themeJsonRaw == null || themeJsonRaw.isBlank() || themeJsonRaw.trim().equals("{}")) {
                return fallbackMobileThemeJson();
            }

            Map<String, Object> raw =
                    MAPPER.readValue(themeJsonRaw, new TypeReference<Map<String, Object>>() {});

            if (raw == null || raw.isEmpty()) {
                return fallbackMobileThemeJson();
            }

            // already correct
            if (raw.containsKey("valuesMobile")) {
                // Still ensure valuesMobile.colors exists so Flutter doesn't fallback
                Object vmObj = raw.get("valuesMobile");
                if (!(vmObj instanceof Map)) return fallbackMobileThemeJson();

                Map<String, Object> vm = (Map<String, Object>) vmObj;
                Object colorsObj = vm.get("colors");
                if (!(colorsObj instanceof Map)) {
                    vm.put("colors", defaultColorsMap());
                } else {
                    // ensure primary exists
                    Map<String, Object> colors = (Map<String, Object>) colorsObj;
                    if (!colors.containsKey("primary")) {
                        colors.putAll(defaultColorsMap());
                    } else {
                        ensureColorDefaults(colors);
                    }
                }

                // write back
                return MAPPER.writeValueAsString(raw);
            }

            // ---- flat -> wrap ----
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("menuType", raw.getOrDefault("menuType", "bottom"));

            Map<String, Object> valuesMobile = new LinkedHashMap<>();

            // colors
            Map<String, Object> colors = new LinkedHashMap<>();
            Object primary = raw.getOrDefault("primary", "#16A34A");
            colors.put("primary", primary);
            colors.put("onPrimary", raw.getOrDefault("onPrimary", "#FFFFFF"));
            colors.put("background", raw.getOrDefault("background", "#FFFFFF"));
            colors.put("surface", raw.getOrDefault("surface", "#FFFFFF"));
            colors.put("label", raw.getOrDefault("label", "#111827"));
            colors.put("body", raw.getOrDefault("body", "#374151"));
            colors.put("border", raw.getOrDefault("border", primary));

            Object error = raw.getOrDefault("error", "#DC2626");
            colors.put("error", error);

            // extras supported by your Flutter code
            colors.put("danger", raw.getOrDefault("danger", error));
            colors.put("muted", raw.getOrDefault("muted", "#9CA3AF"));
            colors.put("success", raw.getOrDefault("success", primary));

            valuesMobile.put("colors", colors);

            // allow passing optional token sections if present, else provide safe defaults
            valuesMobile.put("card", raw.getOrDefault("card", defaultCardMap()));
            valuesMobile.put("search", raw.getOrDefault("search", defaultSearchMap()));
            valuesMobile.put("button", raw.getOrDefault("button", defaultButtonMap()));

            root.put("valuesMobile", valuesMobile);

            return MAPPER.writeValueAsString(root);

        } catch (Exception e) {
            System.out.println("⚠️ Theme normalization failed: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
            // If parsing fails, don't break runtime-config; just return raw OR fallback
            // returning fallback is safer so Flutter won't crash & won't stay green unexpectedly
            return fallbackMobileThemeJson();
        }
    }

    private void ensureColorDefaults(Map<String, Object> colors) {
        Object primary = colors.getOrDefault("primary", "#16A34A");
        colors.putIfAbsent("onPrimary", "#FFFFFF");
        colors.putIfAbsent("background", "#FFFFFF");
        colors.putIfAbsent("surface", "#FFFFFF");
        colors.putIfAbsent("label", "#111827");
        colors.putIfAbsent("body", "#374151");
        colors.putIfAbsent("border", primary);
        Object error = colors.getOrDefault("error", "#DC2626");
        colors.putIfAbsent("error", error);
        colors.putIfAbsent("danger", error);
        colors.putIfAbsent("muted", "#9CA3AF");
        colors.putIfAbsent("success", primary);
    }

    private String fallbackMobileThemeJson() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("menuType", "bottom");

            Map<String, Object> valuesMobile = new LinkedHashMap<>();
            valuesMobile.put("colors", defaultColorsMap());
            valuesMobile.put("card", defaultCardMap());
            valuesMobile.put("search", defaultSearchMap());
            valuesMobile.put("button", defaultButtonMap());

            root.put("valuesMobile", valuesMobile);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"menuType\":\"bottom\",\"valuesMobile\":{\"colors\":{\"primary\":\"#16A34A\",\"onPrimary\":\"#FFFFFF\",\"background\":\"#FFFFFF\",\"surface\":\"#FFFFFF\",\"label\":\"#111827\",\"body\":\"#374151\",\"border\":\"#16A34A\",\"error\":\"#DC2626\",\"danger\":\"#DC2626\",\"muted\":\"#9CA3AF\",\"success\":\"#16A34A\"}}}";
        }
    }

    private Map<String, Object> defaultColorsMap() {
        Map<String, Object> colors = new LinkedHashMap<>();
        colors.put("primary", "#16A34A");
        colors.put("onPrimary", "#FFFFFF");
        colors.put("background", "#FFFFFF");
        colors.put("surface", "#FFFFFF");
        colors.put("label", "#111827");
        colors.put("body", "#374151");
        colors.put("border", "#16A34A");
        colors.put("error", "#DC2626");
        colors.put("danger", "#DC2626");
        colors.put("muted", "#9CA3AF");
        colors.put("success", "#16A34A");
        return colors;
    }

    private Map<String, Object> defaultCardMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("radius", 16);
        m.put("elevation", 4);
        m.put("padding", 12);
        m.put("imageHeight", 120);
        m.put("showShadow", true);
        m.put("showBorder", true);
        return m;
    }

    private Map<String, Object> defaultSearchMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("radius", 16);
        m.put("borderWidth", 1.4);
        m.put("dense", true);
        return m;
    }

    private Map<String, Object> defaultButtonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("radius", 16);
        m.put("height", 48);
        m.put("textSize", 15);
        m.put("fullWidth", true);
        return m;
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
