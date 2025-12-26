package com.build4all.app.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.dto.CiBuildConfigDto;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.repository.ThemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/ci")
public class CiBuildConfigController {

    private static final Logger log = LoggerFactory.getLogger(CiBuildConfigController.class);

    private final AdminUserProjectRepository aupRepo;
    private final AppRuntimeConfigRepository runtimeRepo;
    private final ThemeRepository themeRepo;

    @Value("${ci.callbackToken:}")
    private String token;

    public CiBuildConfigController(AdminUserProjectRepository aupRepo,
                                   AppRuntimeConfigRepository runtimeRepo,
                                   ThemeRepository themeRepo) {
        this.aupRepo = aupRepo;
        this.runtimeRepo = runtimeRepo;
        this.themeRepo = themeRepo;
    }

    /**
     * GitHub Action calls:
     * GET /api/ci/owner-project-links/{linkId}/build-config
     * Header: X-Auth-Token: <ci.callbackToken>
     */
    @GetMapping(
            value = "/owner-project-links/{linkId}/build-config",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> getBuildConfig(
            @RequestHeader(value = "X-Auth-Token", required = false) String xToken,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long linkId
    ) {
        if (!isAuthorized(xToken, auth)) {
            log.warn("CI build-config unauthorized: linkId={}", linkId);
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized"));
        }

        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProjectLink not found: " + linkId));

        AppRuntimeConfig cfg = runtimeRepo.findByApp_Id(link.getId()).orElse(null);

        String nav = cfg != null ? nz(cfg.getNavJson()) : "[]";
        String home = cfg != null ? nz(cfg.getHomeJson()) : "{}";
        String features = cfg != null ? nz(cfg.getEnabledFeaturesJson()) : "[]";
        String branding = cfg != null ? nz(cfg.getBrandingJson()) : "{}";

        String themeJson = resolveThemeJson(link.getThemeId());

        Long currencyId = (link.getCurrency() != null) ? link.getCurrency().getId() : null;
        String currencyCode = (link.getCurrency() != null) ? link.getCurrency().getCode() : null;
        String currencySymbol = (link.getCurrency() != null) ? link.getCurrency().getSymbol() : null;

        String appType = (link.getProject() != null && link.getProject().getProjectType() != null)
                ? link.getProject().getProjectType().name()
                : null;

        CiBuildConfigDto dto = new CiBuildConfigDto(
                String.valueOf(link.getAdmin().getAdminId()),
                String.valueOf(link.getProject().getId()),
                String.valueOf(link.getId()),
                nz(link.getSlug()),

                nz(link.getAppName()),
                appType,

                link.getThemeId(),
                themeJson,
                b64(themeJson),

                currencyId,
                currencyCode,
                currencySymbol,

                nz(link.getLogoUrl()),

                cfg != null ? cfg.getApiBaseUrlOverride() : null,

                nav, home, features, branding,

                b64(nav),
                b64(home),
                b64(features),
                b64(branding)
        );

        return ResponseEntity.ok(dto);
    }

    // ---------- helpers ----------
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

    private String resolveThemeJson(Long themeId) {
        Theme theme = null;

        if (themeId != null) theme = themeRepo.findById(themeId).orElse(null);
        if (theme == null) theme = themeRepo.findByIsActiveTrue().orElse(null);

        if (theme == null || theme.getThemeJson() == null || theme.getThemeJson().isBlank()) return "{}";
        return theme.getThemeJson();
    }

    private static String b64(String s) {
        if (s == null) s = "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
