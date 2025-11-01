package com.build4all.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends a GitHub repository_dispatch to trigger the mobile workflow.
 * - Fully NULL-SAFE (no Map.of with nulls).
 * - Logs clearly when config is missing.
 */
@Service
public class CiBuildService {

    private static final Logger log = LoggerFactory.getLogger(CiBuildService.class);

    private final WebClient web;

    /** Example: https://api.github.com/repos/<user>/<repo>/dispatches */
    @Value("${ci.webhook.url:}")
    private String webhookUrl;

    /** GitHub PAT with "repo" (and usually "workflow") scopes. */
    @Value("${ci.webhook.token:}")
    private String webhookToken;

    /** Public base that reaches your backend callback (must end with /api/ci). */
    @Value("${ci.callbackUrl:}")
    private String callbackBase;

    /** Shared secret that the workflow will send back in X-Auth-Token. */
    @Value("${ci.callbackToken:}")
    private String callbackToken;

    public CiBuildService(WebClient.Builder builder) {
        this.web = builder.build();
    }

    /** Basic guard. */
    public boolean isConfigured() {
        return notBlank(webhookUrl) && notBlank(webhookToken)
            && notBlank(callbackBase) && notBlank(callbackToken);
    }

    /**
     * Dispatch the "owner_app_build" event with all inputs needed by the workflow.
     * Returns true if GitHub responded 2xx, false otherwise. Never throws NPE.
     */
    public boolean dispatchOwnerAndroidBuild(
            long ownerId,
            long projectId,
            String ownerProjectLinkId, // may be null; we will compute fallback
            String slug,               // may be null; we will still send empty
            String appName,            // may be null; we will still send empty
            Long themeId,              // may be null; we will omit it
            String appLogoUrl          // may be null; we will send empty
    ) {
        if (!isConfigured()) {
            log.warn("CI DISPATCH SKIPPED: ci.webhook.url/token or ci.callbackUrl/token not configured.");
            log.debug("webhookUrl={}, webhookToken[set?]={}, callbackBase={}, callbackToken[set?]={}",
                    webhookUrl, notBlank(webhookToken), callbackBase, notBlank(callbackToken));
            return false;
        }

        // Defensive defaults (avoid nulls inside JSON payload).
        final String opl = notBlank(ownerProjectLinkId) ? ownerProjectLinkId : (ownerId + "-" + projectId);
        final String safeSlug = nz(slug);
        final String safeName = nz(appName);
        final String safeLogo = nz(appLogoUrl);
        final String safeCbBase = nz(callbackBase);
        final String safeCbToken = nz(callbackToken);

        // Build payload WITHOUT using Map.of (Map.of throws if any value is null).
        Map<String, Object> clientPayload = new HashMap<>();
        clientPayload.put("OWNER_ID", ownerId);
        clientPayload.put("PROJECT_ID", projectId);
        clientPayload.put("OWNER_PROJECT_LINK_ID", opl);
        clientPayload.put("SLUG", safeSlug);
        clientPayload.put("APP_NAME", safeName);
        clientPayload.put("APP_LOGO_URL", safeLogo);
        if (themeId != null) {
            clientPayload.put("THEME_ID", themeId);
        }
        clientPayload.put("CI_CALLBACK_TOKEN", safeCbToken);
        clientPayload.put("BACKEND_CI_BASE", safeCbBase);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_type", "owner_app_build");
        payload.put("client_payload", clientPayload);

        try {
            ResponseEntity<Void> resp = web.post()
                    .uri(webhookUrl)
                    .header("Authorization", "Bearer " + webhookToken)
                    .header("Accept", "application/vnd.github+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            int code = resp == null ? 0 : resp.getStatusCode().value();
            log.info("repository_dispatch -> HTTP {}", code);
            if (code < 200 || code >= 300) {
                log.warn("CI DISPATCH FAILED (non-2xx). Check repo/action settings & PAT scopes.");
            }
            return code >= 200 && code < 300;
        } catch (Exception ex) {
            log.error("repository_dispatch error: {}", ex.toString());
            return false;
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static String nz(String s) { return s == null ? "" : s; }
}
