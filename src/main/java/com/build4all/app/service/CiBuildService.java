// src/main/java/com/build4all/app/service/CiBuildService.java
package com.build4all.app.service;

import com.build4all.app.dto.CiDispatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;

@Service
public class CiBuildService {

    private static final Logger log = LoggerFactory.getLogger(CiBuildService.class);
    private final WebClient web;

    /** e.g. https://api.github.com/repos/<user>/<repo>/dispatches */
    @Value("${ci.webhook.url:}")
    private String webhookUrl;

    /** GitHub PAT with repo access. */
    @Value("${ci.webhook.token:}")
    private String webhookToken;

    /** Where to upload the logo via Contents API so we only pass a small URL in client_payload */
    @Value("${ci.repo.owner:fatimahh0}")
    private String repoOwner;

    @Value("${ci.repo.name:HobbySphereFlutter}")
    private String repoName;

    @Value("${ci.repo.branch:main}")
    private String repoBranch;

    // Mobile runtime defaults
    @Value("${mobile.apiBaseUrl:http://192.168.1.7:8080}")
    private String mobileApiBaseUrl;

    @Value("${mobile.wsPath:/api/ws}")
    private String mobileWsPath;

    @Value("${mobile.ownerAttachMode:header}")
    private String mobileOwnerAttachMode;

    @Value("${mobile.appRole:both}")
    private String mobileAppRole;

    public CiBuildService(WebClient.Builder builder) {
        this.web = builder
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean isConfigured() {
        return notBlank(webhookUrl) && notBlank(webhookToken);
    }

    /**
     * Upload small logo bytes to GitHub (Contents API) to get a stable raw URL.
     * If appLogoUrl is already absolute (http/https), it’s returned as-is.
     * (Currently not used; we rely on /uploads path + API_BASE_URL on CI side.)
     */
    @SuppressWarnings("unused")
    private String ensureLogoRawUrl(String slug, String appName, String appLogoUrl, byte[] logoBytesOpt) {
        // Use given absolute URL if present
        if (notBlank(appLogoUrl) &&
                (appLogoUrl.startsWith("http://") || appLogoUrl.startsWith("https://"))) {
            return appLogoUrl;
        }
        // Nothing to upload
        if (logoBytesOpt == null || logoBytesOpt.length == 0) {
            log.warn("No logo bytes provided; proceeding without logo.");
            return "";
        }

        String safeSlug = (notBlank(slug) ? slug.trim().toLowerCase() : "app");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "app_icon_" + stamp + ".png";
        String path = "branding/" + safeSlug + "/" + filename;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Add app icon for " + (notBlank(appName) ? appName : safeSlug));
        body.put("content", Base64.getEncoder().encodeToString(logoBytesOpt));
        body.put("branch", repoBranch);

        String contentsApi = "https://api.github.com/repos/" + repoOwner + "/" + repoName + "/contents/" + path;

        try {
            ClientResponse resp = web.put()
                    .uri(contentsApi)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + webhookToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .block();

            if (resp == null) {
                log.error("Contents API returned null");
                return "";
            }

            int code = resp.statusCode().value();
            String respBody = resp.bodyToMono(String.class).blockOptional().orElse("");

            if (code < 200 || code >= 300) {
                log.error("Contents API FAILED (HTTP {}): {}", code, respBody);
                return "";
            }

            String rawUrl = "https://raw.githubusercontent.com/"
                    + repoOwner + "/" + repoName + "/" + repoBranch + "/" + path;
            log.info("Uploaded logo to repo path={} rawUrl={}", path, rawUrl);
            return rawUrl;

        } catch (Exception e) {
            log.error("Contents API error: {}", e.toString());
            return "";
        }
    }

    private static String b64(String s) {
        if (s == null || s.isBlank()) return "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Dispatch repository_dispatch and return FULL result (HTTP code + response body).
     */
    public CiDispatchResult dispatchOwnerAndroidBuild(
            long ownerId,
            long projectId,
            String ownerProjectLinkId,
            String slug,
            String appName,
            String appType,
            Long themeId,
            String appLogoUrl,
            String themeJson,
            byte[] logoBytesOpt,
            Long currencyIdForBuild,
            String apiBaseUrlOverride,
            String navJson,
            String homeJson,
            String enabledFeaturesJson,
            String brandingJson,
            Integer androidVersionCode,
            String androidVersionName
    ) {
        if (!isConfigured()) {
            String msg = "CI DISPATCH SKIPPED: ci.webhook.url/token not configured.";
            log.warn(msg);
            return new CiDispatchResult(false, 0, msg, "");
        }

        final String buildId = UUID.randomUUID().toString();
        final String opl = notBlank(ownerProjectLinkId) ? ownerProjectLinkId : (ownerId + "-" + projectId);

        // ---- normalize strings ----
        String themeJsonNorm    = nz(themeJson);
        String navJsonNorm      = nz(navJson);
        String homeJsonNorm     = nz(homeJson);
        String enabledJsonNorm  = nz(enabledFeaturesJson);
        String brandingJsonNorm = nz(brandingJson);

        // ---- API base URL (override wins) ----
        String effectiveApiBaseUrl = notBlank(apiBaseUrlOverride)
                ? apiBaseUrlOverride.trim()
                : mobileApiBaseUrl;

        // ---- encode JSONs to B64 for Flutter ----
        String themeB64    = b64(themeJsonNorm);
        String navB64      = b64(navJsonNorm);
        String homeB64     = b64(homeJsonNorm);
        String enabledB64  = b64(enabledJsonNorm);
        String brandingB64 = b64(brandingJsonNorm);

        // ---- logoPath (relative) for BRANDING.logoPath ----
        // appLogoUrl is like "/uploads/owner/..."
        String logoPath = nz(appLogoUrl);
        String packageName = "com.build4all.app" + opl;  // opl = OWNER_PROJECT_LINK_ID

        // ================== CONFIG OBJECT (nested) ==================
        Map<String, Object> config = new LinkedHashMap<>();

        config.put("OWNER_ID", ownerId);
        config.put("PROJECT_ID", projectId);
        config.put("OWNER_PROJECT_LINK_ID", opl);
        config.put("SLUG", nz(slug));

        config.put("APP_NAME", nz(appName));
        config.put("APP_TYPE", nz(appType));

        config.put("THEME_ID", themeId);
        config.put("THEME_JSON", themeJsonNorm);
        config.put("THEME_JSON_B64", themeB64);

        config.put("CURRENCY_ID", currencyIdForBuild);
        config.put("CURRENCY_CODE", null);
        config.put("CURRENCY_SYMBOL", null);

        // API info
        config.put("API_BASE_URL_OVERRIDE", nz(apiBaseUrlOverride));
        config.put("API_BASE_URL", nz(effectiveApiBaseUrl));

        // runtime JSONs (raw + B64)
        config.put("NAV_JSON", navJsonNorm);
        config.put("HOME_JSON", homeJsonNorm);
        config.put("ENABLED_FEATURES_JSON", enabledJsonNorm);
        config.put("BRANDING_JSON", brandingJsonNorm);

        config.put("NAV_JSON_B64", navB64);
        config.put("HOME_JSON_B64", homeB64);
        config.put("ENABLED_FEATURES_JSON_B64", enabledB64);
        config.put("BRANDING_JSON_B64", brandingB64);

        // 🔥 VERSION INFO (الجديد)
        config.put("ANDROID_VERSION_CODE", androidVersionCode);
        config.put("ANDROID_VERSION_NAME", nz(androidVersionName));

        // logo info
        config.put("LOGO_PATH", logoPath);

        Map<String, Object> brandingMap = new LinkedHashMap<>();
        brandingMap.put("logoPath", logoPath);
        brandingMap.put("splashColor", "#FFFFFF");
        config.put("BRANDING", brandingMap);

        // extra runtime stuff if you ever need it from workflow
        config.put("WS_PATH", nz(mobileWsPath));
        config.put("OWNER_ATTACH_MODE", nz(mobileOwnerAttachMode));
        config.put("APP_ROLE", nz(mobileAppRole));
        config.put("PACKAGE_NAME", packageName);

        // ================== client_payload (<= 10 top-level keys) ==================
        Map<String, Object> clientPayload = new LinkedHashMap<>();
        clientPayload.put("BUILD_ID", buildId);
        clientPayload.put("CONFIG", config); // كل شي جوّا CONFIG, so only 2 top-level keys

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", "owner_app_build");
        payload.put("client_payload", clientPayload);

        try {
            ClientResponse resp = web.post()
                    .uri(webhookUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + webhookToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchange()
                    .block();

            if (resp == null) {
                String msg = "repository_dispatch -> null response";
                log.error(msg);
                return new CiDispatchResult(false, -1, msg, buildId);
            }

            int code = resp.statusCode().value();
            String body = resp.bodyToMono(String.class).blockOptional().orElse("");

            boolean ok = code >= 200 && code < 300;
            if (ok) {
                log.info("repository_dispatch OK (BUILD_ID={}, HTTP {})", buildId, code);
            } else {
                log.error("repository_dispatch FAILED (BUILD_ID={}, HTTP {}): {}", buildId, code, body);
            }

            return new CiDispatchResult(ok, code, body, buildId);

        } catch (Exception ex) {
            String msg = "repository_dispatch error: " + ex;
            log.error(msg);
            return new CiDispatchResult(false, -2, msg, buildId);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
