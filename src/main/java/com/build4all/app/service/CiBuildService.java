// src/main/java/com/build4all/app/service/CiBuildService.java
package com.build4all.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class CiBuildService {
    private static final Logger log = LoggerFactory.getLogger(CiBuildService.class);

    private final WebClient webClient;

    // If these are missing, we treat CI as disabled.
    @Value("${ci.webhook.url:}")
    private String webhookUrl;

    @Value("${ci.webhook.token:}")
    private String webhookToken;

    public CiBuildService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank() &&
               webhookToken != null && !webhookToken.isBlank();
    }

    public void triggerOwnerBuild(Long ownerId, Long projectId, String slug) {
        if (!isEnabled()) {
            log.info("CI trigger skipped (ci.webhook.* not configured). ownerId={}, projectId={}", ownerId, projectId);
            return;
        }

        try {
            // You can add more keys your pipeline expects
            Map<String, Object> payload = Map.of(
                "OWNER_ID", ownerId,
                "PROJECT_ID", projectId,
                "SLUG", slug,
                // Often useful to pass a composite ID used by the Flutter app
                "OWNER_PROJECT_LINK_ID", ownerId + "-" + projectId
            );

            webClient.post()
                     .uri(webhookUrl)
                     .header("X-Auth-Token", webhookToken)
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(payload)
                     .retrieve()
                     .toBodilessEntity()
                     .block();

            log.info("CI trigger sent for ownerId={}, projectId={}, slug={}", ownerId, projectId, slug);
        } catch (Exception ex) {
            // Non-fatal: approval still succeeds even if CI fails to start
            log.warn("CI trigger failed for ownerId={}, projectId={}: {}", ownerId, projectId, ex.getMessage());
        }
    }
}
