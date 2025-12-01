package com.build4all.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

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

    /** GitHub PAT with "repo" scope (and usually "workflow"). */
    @Value("${ci.webhook.token:}")
    private String webhookToken;

    /** Where to upload the logo via Contents API so we only pass a small URL in client_payload */
    @Value("${ci.repo.owner:fatimahh0}")
    private String repoOwner;

    @Value("${ci.repo.name:HobbySphereFlutter}")
    private String repoName;

    @Value("${ci.repo.branch:main}")
    private String repoBranch;

    // Mobile runtime defaults (nested into RUNTIME to keep ≤10 top-level props)
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
     */
    private String ensureLogoRawUrl(String slug, String appName, String appLogoUrl, byte[] logoBytesOpt) {
        // Use given absolute URL if present
        if (notBlank(appLogoUrl) && (appLogoUrl.startsWith("http://") || appLogoUrl.startsWith("https://"))) {
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

            // public raw URL
            String rawUrl = "https://raw.githubusercontent.com/" + repoOwner + "/" + repoName + "/" + repoBranch + "/" + path;
            log.info("Uploaded logo to repo path={} rawUrl={}", path, rawUrl);
            return rawUrl;
        } catch (Exception e) {
            log.error("Contents API error: {}", e.toString());
            return "";
        }
    }

    /**
     * Dispatch the workflow with ≤10 top-level properties.
     * We pass a small APP_LOGO_URL (public), theme JSON, and nest runtime values under RUNTIME.
     * @param currencyIdForBuild 
     */
    public boolean dispatchOwnerAndroidBuild(
            long ownerId,
            long projectId,
            String ownerProjectLinkId,  // pass real link.getId().toString()
            String slug,
            String appName,
            Long themeId,
            String appLogoUrl,
            String themeJson,
            byte[] logoBytesOpt,
            Long currencyIdForBuild
    ) {
        if (!isConfigured()) {
            log.warn("CI DISPATCH SKIPPED: ci.webhook.url/token not configured.");
            return false;
        }

        final String buildId = UUID.randomUUID().toString();
        final String opl = notBlank(ownerProjectLinkId) ? ownerProjectLinkId : (ownerId + "-" + projectId);

        // Resolve logo URL (upload when needed)
        String resolvedLogoUrl = ensureLogoRawUrl(slug, appName, appLogoUrl, logoBytesOpt);

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("API_BASE_URL", mobileApiBaseUrl);
        runtime.put("WS_PATH", mobileWsPath);
        runtime.put("OWNER_ATTACH_MODE", mobileOwnerAttachMode);
        runtime.put("APP_ROLE", mobileAppRole);

        Map<String, Object> clientPayload = new LinkedHashMap<>();
        clientPayload.put("BUILD_ID", buildId);
        clientPayload.put("OWNER_ID", ownerId);
        clientPayload.put("PROJECT_ID", projectId);
        clientPayload.put("OWNER_PROJECT_LINK_ID", opl);
        clientPayload.put("SLUG", nz(slug));
        clientPayload.put("APP_NAME", nz(appName));
        if (themeId != null) clientPayload.put("THEME_ID", themeId);
        clientPayload.put("RUNTIME", runtime);               // 1 property
        clientPayload.put("APP_LOGO_URL", nz(resolvedLogoUrl));
        clientPayload.put("APP_THEME_JSON", nz(themeJson));  // ✅ palette JSON for workflow

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
                log.error("repository_dispatch -> null response");
                return false;
            }

            int code = resp.statusCode().value();
            String body = resp.bodyToMono(String.class).blockOptional().orElse("");
            if (code >= 200 && code < 300) {
                log.info("repository_dispatch OK (BUILD_ID={}, HTTP {})", buildId, code);
                return true;
            } else {
                log.error("repository_dispatch FAILED (HTTP {}): {}", code, body);
                return false;
            }
        } catch (Exception ex) {
            log.error("repository_dispatch error: {}", ex.toString());
            return false;
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String nz(String s) { return s == null ? "" : s; }
}
