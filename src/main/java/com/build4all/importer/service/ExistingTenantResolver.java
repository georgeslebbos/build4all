package com.build4all.importer.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import org.springframework.stereotype.Service;

/**
 * Resolves an existing AdminUserProject (tenant/app) by ownerProjectId.
 *
 * ✅ We do NOT create anything from Excel.
 * ✅ SETUP sheet is not used.
 */
@Service
public class ExistingTenantResolver {

    private final AdminUserProjectRepository aupRepo;

    public ExistingTenantResolver(AdminUserProjectRepository aupRepo) {
        this.aupRepo = aupRepo;
    }

    public Resolved resolveExisting(Long ownerProjectId) {
        AdminUserProject aup = aupRepo.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalStateException("AdminUserProject not found: " + ownerProjectId));

        if (aup.getProject() == null || aup.getProject().getId() == null) {
            throw new IllegalStateException("AdminUserProject.project is null for ownerProjectId=" + ownerProjectId);
        }

        return new Resolved(aup.getProject().getId(), aup.getId(), aup.getSlug());
    }

    public record Resolved(Long projectId, Long ownerProjectId, String slug) {}
}
