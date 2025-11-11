// src/main/java/com/build4all/app/service/ApkManifestPullService.java
package com.build4all.app.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class ApkManifestPullService {

    private static final Logger log = LoggerFactory.getLogger(ApkManifestPullService.class);

    private final AdminUserProjectRepository aupRepo;
    private final WebClient web;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${ci.repo.owner}")  private String owner;
    @Value("${ci.repo.name}")   private String repo;
    @Value("${ci.repo.branch:main}") private String branch;

    public ApkManifestPullService(AdminUserProjectRepository aupRepo, WebClient.Builder builder) {
        this.aupRepo = aupRepo;
        this.web = builder.build();
    }

    /** Pulls builds/{ownerId}/{projectId}/{slug}/latest.json and writes apkUrl. */
    public AdminUserProject updateLinkFromManifest(long ownerId, long projectId, String slug) {
        String path = String.format("builds/%d/%d/%s/latest.json", ownerId, projectId, slug.toLowerCase());
        String rawUrl = String.format("https://raw.githubusercontent.com/%s/%s/%s/%s", owner, repo, branch, path);

        try {
            String body = web.get()
                    .uri(rawUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null || body.isBlank()) throw new IllegalStateException("Empty manifest body");
            JsonNode root = om.readTree(body);
            String apkUrl = root.path("apkUrl").asText("");
            if (apkUrl.isBlank()) throw new IllegalStateException("apkUrl missing in manifest");

            AdminUserProject link = aupRepo
                    .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slug.toLowerCase())
                    .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

            // Skip if no change
            if (apkUrl.equals(link.getApkUrl())) {
                log.info("No change in apkUrl for {}/{}/{} -> {}", ownerId, projectId, slug, apkUrl);
                return link;
            }

            link.setApkUrl(apkUrl);
            aupRepo.save(link);

            log.info("APK URL updated: owner={} project={} slug={} -> {}", ownerId, projectId, slug, apkUrl);
            return link;

        } catch (Exception ex) {
            log.error("Failed to pull manifest for {}/{}/{}: {}", ownerId, projectId, slug, ex.toString());
            throw new RuntimeException(ex);
        }
    }
}
