// src/main/java/com/build4all/app/service/ApkManifestScheduler.java
package com.build4all.app.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ApkManifestScheduler {
    private static final Logger log = LoggerFactory.getLogger(ApkManifestScheduler.class);

    private final AdminUserProjectRepository aupRepo;
    private final ApkManifestPullService pull;

    public ApkManifestScheduler(AdminUserProjectRepository aupRepo, ApkManifestPullService pull) {
        this.aupRepo = aupRepo;
        this.pull = pull;
    }

    /** every 5 minutes; tweak as you like */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void refreshAllActive() {
        List<AdminUserProject> links = aupRepo.findAll(); // Optionally filter ACTIVE only
        for (AdminUserProject link : links) {
            try {
                if (link.getSlug() == null || link.getAdmin() == null || link.getProject() == null) continue;
                pull.updateLinkFromManifest(
                    link.getAdmin().getAdminId(),
                    link.getProject().getId(),
                    link.getSlug()
                );
            } catch (Exception ex) {
                log.warn("Skip link {}: {}", link.getId(), ex.toString());
            }
        }
    }
}
