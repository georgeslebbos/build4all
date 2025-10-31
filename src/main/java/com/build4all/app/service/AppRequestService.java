package com.build4all.app.service;

import com.build4all.app.domain.AppRequest;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.repository.ThemeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AppRequestService {

    private final AppRequestRepository appRequestRepo;
    private final AdminUserProjectRepository aupRepo;
    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;
    private final ThemeRepository themeRepo;
    private final CiBuildService ciBuildService;

    public AppRequestService(AppRequestRepository appRequestRepo,
                             AdminUserProjectRepository aupRepo,
                             AdminUsersRepository adminRepo,
                             ProjectRepository projectRepo,
                             ThemeRepository themeRepo,
                             CiBuildService ciBuildService) {
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.themeRepo = themeRepo;
        this.ciBuildService = ciBuildService;
    }

    /**
     * Create a request WITHOUT auto-approval (kept for backward compatibility).
     * If you want auto-approval, use createAndAutoApprove(...) instead.
     */
    public AppRequest createRequest(Long ownerId, Long projectId,
                                    String appName, String slug,
                                    String logoUrl, Long themeId, String notes) {
        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(slugifyOrKeep(slug, appName));
        r.setLogoUrl(logoUrl);
        r.setThemeId(themeId); // may be null
        r.setNotes(notes);
        // status is left to entity default (likely "PENDING")
        return appRequestRepo.save(r);
    }

    /**
     * NEW: Create AND auto-approve in one shot.
     * - Persists the AppRequest with status APPROVED
     * - Provisions/updates AdminUserProject link
     * - Triggers CI build
     */
    @Transactional
    public AdminUserProject createAndAutoApprove(Long ownerId, Long projectId,
                                                 String appName, String slug,
                                                 String logoUrl, Long themeId, String notes) {

        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(slugifyOrKeep(slug, appName));
        r.setLogoUrl(logoUrl);
        r.setThemeId(themeId);
        r.setNotes(notes);
        r.setStatus("APPROVED"); // mark immediately as APPROVED
        r = appRequestRepo.save(r);

        return provisionAndTrigger(r); // main workflow
    }

    /**
     * Manual approve (kept as-is for compatibility).
     * Now delegates to the same provisioning/CI logic used by auto-approve.
     */
    @Transactional
    public AdminUserProject approve(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }

        // Flip status and persist before provisioning
        req.setStatus("APPROVED");
        appRequestRepo.save(req);

        return provisionAndTrigger(req);
    }

    @Transactional
    public void reject(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }
        req.setStatus("REJECTED");
        appRequestRepo.save(req);
    }

    @Transactional
    public AdminUserProject setApkUrl(Long adminId, Long projectId, String apkUrl) {
        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_Id(adminId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found"));
        link.setApkUrl(apkUrl);
        return aupRepo.save(link);
    }
 


    // -------------------------
    // Internal helpers
    // -------------------------

    /**
     * Shared logic for both manual approve and auto-approve:
     * - Resolve owner/project
     * - Pick theme (requested or default active)
     * - Ensure/Update AdminUserProject link (slug, dates, license, ACTIVE)
     * - Trigger CI with all inputs
     */
    private AdminUserProject provisionAndTrigger(AppRequest req) {
        AdminUser owner = adminRepo.findById(req.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Owner(admin) not found"));
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Long chosenThemeId = resolveThemeId(req.getThemeId());

        String slug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? req.getSlug().trim().toLowerCase()
                : slugify(req.getAppName());

        LocalDate now = LocalDate.now();
        LocalDate end = now.plusMonths(1);

        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_Id(owner.getAdminId(), project.getId())
                .orElseGet(() -> new AdminUserProject(owner, project, null, now, end));

        link.setStatus("ACTIVE");
        link.setSlug(slug);
        link.setValidFrom(now);
        link.setEndTo(end);
        link.setThemeId(chosenThemeId);

        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + owner.getAdminId() + "-" + project.getId() + "-" + now);
        }

        // ❗️ KEY LINE: ensure create/auto returns apkUrl = null.
        // If there’s any old placeholder value, wipe it now.
        link.setApkUrl(null);

        aupRepo.save(link);

        // Fire CI build (non-blocking). The callback will set the real apkUrl later.
        ciBuildService.triggerOwnerBuild(
                owner.getAdminId(),
                project.getId(),
                slug,
                chosenThemeId,
                req.getAppName(),
                req.getLogoUrl()
        );

        return link;
    }

    private Long resolveThemeId(Long requested) {
        if (requested != null) {
            return themeRepo.findById(requested).map(Theme::getId)
                    .orElseGet(() -> themeRepo.findByIsActiveTrue().map(Theme::getId).orElse(null));
        }
        return themeRepo.findByIsActiveTrue().map(Theme::getId).orElse(null);
    }

    private static String slugifyOrKeep(String maybeSlug, String fallbackName) {
        if (maybeSlug != null && !maybeSlug.isBlank()) {
            return slugify(maybeSlug);
        }
        return slugify(fallbackName);
    }

    private static String slugify(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
