package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; // <-- IMPORTANT: java.nio.file.Paths (no swagger Paths!)
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserProjectService {

    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository linkRepo;

    public AdminUserProjectService(AdminUsersRepository adminRepo,
                                   ProjectRepository projectRepo,
                                   AdminUserProjectRepository linkRepo) {
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.linkRepo = linkRepo;
    }

    /** List all apps (rows) for an owner (adminId). */
    @Transactional(readOnly = true)
    public List<AdminAppAssignmentResponse> list(Long adminId) {
        return linkRepo.findByAdmin_AdminId(adminId).stream()
            .map(l -> new AdminAppAssignmentResponse(
                l.getProject().getId(),
                l.getProject().getProjectName(),
                nz(l.getAppName()),
                nz(l.getSlug()),
                nz(l.getStatus()),
                nz(l.getLicenseId()),
                l.getValidFrom(),
                l.getEndTo(),
                l.getThemeId(),
                nz(l.getApkUrl()),
                nz(l.getLogoUrl())
            ))
            .toList();
    }

    /** Get one app row (owner+project+slug). */
    @Transactional(readOnly = true)
    public AdminUserProject get(Long adminId, Long projectId, String slug) {
        return linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
            .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
    }

    /**
     * Create or update a single APP under (owner, project) keyed by slug.
     * - If slug omitted: derive from appName (slugify)
     * - Ensure uniqueness per (owner, project) by appending -2, -3...
     */
    @Transactional
    public void assign(Long adminId, AdminAppAssignmentRequest req) {
        AdminUser admin = adminRepo.findByAdminId(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Project project = projectRepo.findById(req.getProjectId())
            .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        String baseSlug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? slugify(req.getSlug())
                : slugify(req.getAppName());
        String uniqueSlug = ensureUniqueSlug(admin.getAdminId(), project.getId(), baseSlug);

        AdminUserProject link = linkRepo
            .findByAdmin_AdminIdAndProject_IdAndSlug(admin.getAdminId(), project.getId(), uniqueSlug)
            .orElse(null);

        LocalDate now = LocalDate.now();

        if (link == null) {
            link = new AdminUserProject(
                admin,
                project,
                req.getLicenseId(),
                req.getValidFrom() != null ? req.getValidFrom() : now,
                req.getEndTo() != null ? req.getEndTo() : now.plusMonths(1)
            );
            link.setStatus("ACTIVE");
            link.setSlug(uniqueSlug);
        } else {
            if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
            if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
            if (req.getEndTo() != null) link.setEndTo(req.getEndTo());
        }

        if (req.getAppName() != null && !req.getAppName().isBlank()) {
            link.setAppName(req.getAppName());
        }
        if (req.getThemeId() != null) {
            link.setThemeId(req.getThemeId());
        }

        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + admin.getAdminId() + "-" + project.getId() + "-" + now + "-" + uniqueSlug);
        }

        // Clear stale APK on (re)assignment (optional)
        link.setApkUrl(null);

        linkRepo.save(link);
    }

    /** Upload & save logo, return its public URL. */
    @Transactional
    public String updateAppLogo(Long adminId, Long projectId, String slug, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is empty");
        }

        var link = linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

        Path uploadDir = Paths.get("uploads/owner", String.valueOf(adminId), String.valueOf(projectId), slugify(slug));
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        String original = file.getOriginalFilename() == null ? "logo.png" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : ".png";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safe = UUID.randomUUID() + "_" + stamp + ext;

        Files.copy(file.getInputStream(), uploadDir.resolve(safe), StandardCopyOption.REPLACE_EXISTING);

        String publicUrl = "/uploads/owner/" + adminId + "/" + projectId + "/" + slugify(slug) + "/" + safe;

        link.setLogoUrl(publicUrl);
        linkRepo.save(link);

        return publicUrl;
    }

    /** Update only license/validity for an existing app (owner+project+slug). */
    @Transactional
    public void updateLicense(Long adminId, Long projectId, String slug, AdminAppAssignmentRequest req) {
        AdminUserProject link = linkRepo
            .findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
            .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

        if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
        if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
        if (req.getEndTo() != null) link.setEndTo(req.getEndTo());

        linkRepo.save(link);
    }

    /** Delete one app (row) under owner+project by slug. */
    @Transactional
    public void remove(Long adminId, Long projectId, String slug) {
        linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
            .ifPresent(linkRepo::delete);
    }

    // ---------- helpers ----------

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    /** Ensure uniqueness per (owner, project) by appending -2, -3, ... */
    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";
        String candidate = baseSlug;
        int i = 2;
        while (linkRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;
            if (i > 500) {
                throw new IllegalStateException("Could not generate a unique slug");
            }
        }
        return candidate;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
