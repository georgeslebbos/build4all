package com.build4all.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Sends a GitHub repository_dispatch with all inputs the workflow needs.
 * The workflow will build the APK, create a release, then call back your backend with the APK URL.
 */
@Service
public class CiBuildService {

    private static final Logger log = LoggerFactory.getLogger(CiBuildService.class);

    private final WebClient webClient;

    @Value("${ci.webhook.url:}")
    private String webhookUrl;

    @Value("${ci.webhook.token:}")
    private String webhookToken;

    @Value("${ci.callbackToken:}")
    private String callbackToken;

    @Value("${ci.callbackUrl:}")
    private String callbackBaseUrl;

    public CiBuildService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    /** Basic guard: require both URL and token */
    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank()
            && webhookToken != null && !webhookToken.isBlank();
    }

    /**
     * Triggers the "owner_app_build" workflow via repository_dispatch.
     * @param ownerId   owner id
     * @param projectId project id
     * @param slug      app slug for release naming
     * @param themeId   theme id (if your workflow needs it)
     * @param appName   pretty app name
     * @param logoUrl   logo url (optional)
     */
    public void triggerOwnerBuild(Long ownerId, Long projectId, String slug,
                                  Long themeId, String appName, String logoUrl) {

        if (!isEnabled()) {
            log.info("CI trigger skipped (ci.webhook.* not configured). ownerId={}, projectId={}", ownerId, projectId);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                "event_type", "owner_app_build",
                "client_payload", Map.of(
                    "OWNER_ID", ownerId,
                    "PROJECT_ID", projectId,
                    "OWNER_PROJECT_LINK_ID", ownerId + "-" + projectId,
                    "SLUG", slug,
                    "APP_NAME", appName,
                    "APP_LOGO_URL", logoUrl == null ? "" : logoUrl,
                    "THEME_ID", themeId,
                    // callback info (shared secret + base URL)
                    "CI_CALLBACK_TOKEN", nullSafe(callbackToken),
                    "BACKEND_CI_BASE", nullSafe(callbackBaseUrl)
                )
            );

            webClient.post()
                     .uri(webhookUrl)
                     .header("Authorization", "Bearer " + webhookToken) // GitHub PAT
                     .header("Accept", "application/vnd.github+json")
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(payload)
                     .retrieve()
                     .toBodilessEntity()
                     .block();

            log.info("repository_dispatch sent: ownerId={}, projectId={}, slug={}, themeId={}",
                    ownerId, projectId, slug, themeId);
        } catch (Exception ex) {
            log.warn("CI trigger failed: ownerId={}, projectId={}, error={}",
                    ownerId, projectId, ex.toString());
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
