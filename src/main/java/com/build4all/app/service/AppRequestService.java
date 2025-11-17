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

    /** Legacy JSON create (kept). */
    public AppRequest createRequest(Long ownerId, Long projectId,
                                    String appName, String slug,
                                    String logoUrl, Long themeId, String notes) {
        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(slugifyOrFallback(slug, appName));
        r.setLogoUrl(logoUrl);
        r.setThemeId(themeId);
        r.setNotes(notes);
        return appRequestRepo.save(r);
    }

    /**
     * Create AND auto-approve (multipart logo supported).
     * - Enforces unique slug per (owner, project)
     * - Saves logo to /uploads/owner/{owner}/{project}/{slug}/<uuid>_<stamp>.<ext>
     * - Triggers CI with all inputs (owner/project/link/slug/name/theme/logo)
     */
    @Transactional
    public AdminUserProject createAndAutoApprove(Long ownerId, Long projectId,
                                                 String appName, String slug,
                                                 MultipartFile logoFile,
                                                 Long themeId, String notes) throws IOException {
        String base = (slug != null && !slug.isBlank()) ? slugify(slug) : slugify(appName);
        String uniqueSlug = ensureUniqueSlug(ownerId, projectId, base);

        String logoUrl = null;
        byte[] logoBytes = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            logoUrl = saveOwnerAppLogoToUploads(ownerId, projectId, uniqueSlug, logoFile);
            logoBytes = logoFile.getBytes(); // pass to CiBuildService to upload to repo
        }

        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(uniqueSlug);
        r.setLogoUrl(logoUrl);
        r.setThemeId(themeId);
        r.setNotes(notes);
        r.setStatus("APPROVED");
        r = appRequestRepo.save(r);

        return provisionAndTrigger(r, logoBytes);
    }

    @Transactional
    public AdminUserProject approve(Long requestId) {
        AppRequest req = appRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request already decided");
        }
        req.setStatus("APPROVED");
        appRequestRepo.save(req);
        // no logo bytes here; only relative url (if any)
        return provisionAndTrigger(req, null);
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

    // ---------- Artifact setters (APK/AAB/IPA) ----------

    @Transactional
    public AdminUserProject setApkUrl(Long adminId, Long projectId, String slug, String apkUrl) {
        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject app not found"));
        link.setApkUrl(apkUrl);
        log.info("Saved apkUrl via owner/project/slug: ownerId={}, projectId={}, slug={}, url={}", adminId, projectId, slug, apkUrl);
        return aupRepo.save(link);
    }

    /** Direct by PK (used by CI callback). */
    @Transactional
    public void setApkUrlByLinkId(Long linkId, String apkUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setApkUrl(apkUrl);
        aupRepo.save(link);
        log.info("Saved apkUrl (no-owner-check) linkId={} -> {}", linkId, apkUrl);
    }

    // --- Owner-checked variants for relative paths under /uploads (kept) ---

    @Transactional
    public void setApkUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setApkUrl(normalizeRel(relUrl));
        aupRepo.save(row);
        log.info("Saved apkUrl (owner-checked) ownerId={} linkId={} -> {}", ownerId, linkId, relUrl);
    }
    
    /** Direct by PK for AAB/bundle (used by CI callback). */
    @Transactional
    public void setBundleUrlByLinkId(Long linkId, String bundleUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setBundleUrl(bundleUrl);
        aupRepo.save(link);
        log.info("Saved bundleUrl (no-owner-check) linkId={} -> {}", linkId, bundleUrl);
    }


    @Transactional
    public void setIpaUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setIpaUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }

    @Transactional
    public void setIpaUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setIpaUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }

    @Transactional
    public void setApkUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setApkUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }

    @Transactional
    public void setBundleUrlByLinkId(Long ownerId, Long linkId, String relUrl) {
        AdminUserProject row = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("App link not found"));
        if (row.getAdmin() == null || !ownerId.equals(row.getAdmin().getAdminId())) {
            throw new SecurityException("Forbidden: link does not belong to this owner");
        }
        row.setBundleUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }

    @Transactional
    public void setBundleUrlByOwnerProjectSlug(Long ownerId, Long projectId, String slug, String relUrl) {
        AdminUserProject row = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
        row.setBundleUrl(normalizeRel(relUrl));
        aupRepo.save(row);
    }

    // ---------- internal ----------

    private AdminUserProject provisionAndTrigger(AppRequest req, byte[] logoBytesOpt) {
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

        // clear stale artifacts before new build
        link.setApkUrl(null);
        link.setIpaUrl(null);
        link.setBundleUrl(null);
        link = aupRepo.save(link);

        String ownerProjectLinkId = String.valueOf(link.getId()); // REAL PK

        boolean ok = ciBuildService.dispatchOwnerAndroidBuild(
                owner.getAdminId(),
                project.getId(),
                ownerProjectLinkId,
                uniqueSlug,
                req.getAppName(),
                chosenThemeId,
                req.getLogoUrl(),   // may be relative; service will upload logoBytesOpt to repo
                logoBytesOpt
        );

        if (!ok) {
            log.warn("CI dispatch failed or skipped; apkUrl remains null until a successful run.");
        } else {
            log.info("CI dispatch OK (ownerId={}, projectId={}, linkId={}, slug={})",
                    owner.getAdminId(), project.getId(), link.getId(), uniqueSlug);
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

    /** Save under uploads/, return '/uploads/...' URL */
    private String saveOwnerAppLogoToUploads(Long ownerId, Long projectId, String slug, MultipartFile file)
            throws IOException {
        String cleanSlug = slugify(slug);
        Path dir = Paths.get("uploads/owner", String.valueOf(ownerId), String.valueOf(projectId), cleanSlug);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String original = (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                ? "logo.png" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : ".png";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safe = UUID.randomUUID() + "_" + stamp + ext;

        Files.copy(file.getInputStream(), dir.resolve(safe), StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/owner/" + ownerId + "/" + projectId + "/" + cleanSlug + "/" + safe;
    }

    private static String normalizeRel(String rel) {
        if (rel == null || rel.isBlank()) throw new IllegalArgumentException("Empty path");
        String s = rel.replace('\\', '/').trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (!s.startsWith("/uploads/")) throw new IllegalArgumentException("Path must be under /uploads/");
        return s;
    }

    private static String slugifyOrFallback(String maybeSlug, String fallbackName) {
        if (maybeSlug != null && !maybeSlug.isBlank()) return slugify(maybeSlug);
        return slugify(fallbackName);
    }

    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";
        String candidate = baseSlug;
        int i = 2;
        while (aupRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;
            if (i > 500) throw new IllegalStateException("Could not generate a unique slug");
        }
        return candidate;
    }

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
    
    @Transactional
    public AdminUserProject prepareRebuildByLink(Long ownerId, Long linkId) {
        AdminUserProject link = aupRepo.findByIdAndAdmin_AdminId(linkId, ownerId)
        	    .orElseThrow(() -> new IllegalArgumentException("Link not found for this owner"));

        link.setStatus("IN_PRODUCTION");
        link.setApkUrl(null); 
        return aupRepo.save(link);
    }
    
    @Transactional(readOnly = true)
    public String enqueueBuild(Long ownerId, Long projectId, AdminUserProject link) {
        // Sanity: fallbacks from link
        final Long themeId = link.getThemeId();
        final String slug    = (link.getSlug() == null) ? "app" : link.getSlug().trim().toLowerCase();
        final String appName = (link.getAppName() == null) ? "My App" : link.getAppName().trim();
        final String logoUrl = link.getLogoUrl(); // may be relative; CiBuildService should handle it

        boolean ok = ciBuildService.dispatchOwnerAndroidBuild(
                ownerId,
                projectId,
                String.valueOf(link.getId()), // ownerProjectLinkId
                slug,
                appName,
                themeId,
                logoUrl,
                null // no fresh bytes on rebuild; use existing URL in the CI service
        );

        if (!ok) {
            log.warn("CI dispatch failed/was skipped (ownerId={}, projectId={}, linkId={}, slug={})",
                    ownerId, projectId, link.getId(), slug);
        } else {
            log.info("CI dispatch OK (ownerId={}, projectId={}, linkId={}, slug={})",
                    ownerId, projectId, link.getId(), slug);
        }

        // Return a simple client-visible token (not required by CI, just for UX)
        return "job-" + link.getId() + "-" + System.currentTimeMillis();
    }


}
