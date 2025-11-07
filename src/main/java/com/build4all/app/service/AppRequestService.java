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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class AppRequestService {

    private static final Logger log = LoggerFactory.getLogger(AppRequestService.class);

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

    /** Create request without auto-approval (kept for compatibility). */
    public AppRequest createRequest(Long ownerId, Long projectId,
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
        // status defaults to PENDING
        return appRequestRepo.save(r);
    }

    /** Create AND auto-approve. */
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
        r.setStatus("APPROVED");
        r = appRequestRepo.save(r);

        return provisionAndTrigger(r);
    }

    /** Approve a PENDING request then run provisioning/CI logic. */
    @Transactional
    public AdminUserProject approve(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }
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

    /** Called by CI callback controller to persist the APK URL (app-scoped). */
    @Transactional
    public AdminUserProject setApkUrl(Long adminId, Long projectId, String slug, String apkUrl) {
        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slug)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject app not found"));
        link.setApkUrl(apkUrl);
        return aupRepo.save(link);
    }

    /** NEW: persist by row id (AdminUserProject.id). */
    @Transactional
    public void setApkUrlByLinkId(Long linkId, String apkUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setApkUrl(apkUrl);
        aupRepo.save(link);
    }

    /** NEW: persist by (ownerId, projectId, slug). */
    @Transactional
    public void setApkUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setApkUrl(normalizeRel(relUrl));
    }
    
    private static String normalizeRel(String rel) {
        if (rel == null || rel.isBlank()) {
            throw new IllegalArgumentException("Empty apk path");
        }
        String s = rel.replace('\\', '/').trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (!s.startsWith("/uploads/")) {
            throw new IllegalArgumentException("APK path must be under /uploads/");
        }
        return s;
    }

    /** Persist relative IPA path by link id, with owner validation. */
    @Transactional
    public void setIpaUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setIpaUrl(normalizeRel(relUrl));
    }

    /** Persist relative IPA path by (owner + project + slug). */
    @Transactional
    public void setIpaUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setIpaUrl(normalizeRel(relUrl));
    }

    /** Persist relative APK path by (owner + project + slug). */
    @Transactional
    public void setApkUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setApkUrl(normalizeRel(relUrl));
    }
   
    // ---------- internal helpers ----------

    /**
     * Provision a DISTINCT app row per (owner, project, slug) and trigger CI.
     */
    private AdminUserProject provisionAndTrigger(AppRequest req) {
        AdminUser owner = adminRepo.findById(req.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Owner(admin) not found"));
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Long chosenThemeId = resolveThemeId(req.getThemeId());
        String desiredSlug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? req.getSlug().trim().toLowerCase()
                : slugify(req.getAppName());
        String uniqueSlug = ensureUniqueSlug(owner.getAdminId(), project.getId(), desiredSlug);

        LocalDate now = LocalDate.now();
        LocalDate end = now.plusMonths(1);

        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(owner.getAdminId(), project.getId(), uniqueSlug)
                .orElseGet(() -> {
                    AdminUserProject n = new AdminUserProject(owner, project, null, now, end);
                    n.setSlug(uniqueSlug);
                    return n;
                });

        link.setStatus("ACTIVE");
        link.setAppName(req.getAppName());
        link.setLogoUrl(req.getLogoUrl());
        link.setValidFrom(now);
        link.setEndTo(end);
        link.setThemeId(chosenThemeId);

        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + owner.getAdminId() + "-" + project.getId() + "-" + now + "-" + uniqueSlug);
        }

        // Clear any old APK URL so UI won't show stale value.
        link.setApkUrl(null);
        link = aupRepo.save(link);

        // Build OPL (never null).
        String opl = owner.getAdminId() + "-" + project.getId();

        boolean ok = ciBuildService.dispatchOwnerAndroidBuild(
                owner.getAdminId(),
                project.getId(),
                opl,
                uniqueSlug,
                req.getAppName(),
                chosenThemeId,
                req.getLogoUrl()
        );

        if (!ok) {
            log.warn("CI dispatch did NOT return 2xx (apkUrl will remain null until a successful run).");
        }
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
        if (maybeSlug != null && !maybeSlug.isBlank()) return slugify(maybeSlug);
        return slugify(fallbackName);
    }

    /** Ensure slug uniqueness per (owner, project) by appending -2, -3, ... */
    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";
        String candidate = baseSlug;
        int i = 2;
        while (aupRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;
            if (i > 500) { // safety
                throw new IllegalStateException("Could not generate a unique slug");
            }
        }
        return candidate;
    }

    public AdminUserProject createAndAutoApproveWithLogoFile(
            Long ownerId, Long projectId,
            String appName, String slug,
            MultipartFile logoFile,
            Long themeId, String notes
    ) throws IOException {

        String logoUrl = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            logoUrl = saveOwnerAppLogoToUploads(ownerId, projectId, slug, logoFile);
        }

        return createAndAutoApprove(ownerId, projectId, appName, slug, logoUrl, themeId, notes);
    }

    /** Save under uploads/, return "/uploads/..." URL */
    private String saveOwnerAppLogoToUploads(Long ownerId, Long projectId, String slug, MultipartFile file) throws IOException {
        Path dir = Paths.get("uploads/owner", String.valueOf(ownerId), String.valueOf(projectId), slugify1(slug));
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String original = file.getOriginalFilename() == null ? "logo.png" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : ".png";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safe = UUID.randomUUID() + "_" + stamp + ext;

        Files.copy(file.getInputStream(), dir.resolve(safe), StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/owner/" + ownerId + "/" + projectId + "/" + slugify(slug) + "/" + safe;
    }

    private static String slugify1(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");
    }

    private static String slugify(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
    
    @Transactional
    public void setBundleUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setBundleUrl(normalizeRel(relUrl)); // <-- add bundleUrl column in entity if not present
        aupRepo.save(row);
    }

    /** NEW: persist relative AAB path by (owner + project + slug). */
    @Transactional
    public void setBundleUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setBundleUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }
}
