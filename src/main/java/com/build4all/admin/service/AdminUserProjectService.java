package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
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
/**
 * Business logic for managing AdminUserProject links (AUP).
 *
 * AdminUserProject = an "app assignment" row that connects:
 *   AdminUser (owner/manager) + Project
 * and stores metadata such as:
 *   slug, appName, license validity, build artifact URLs, themeId, currency, logoUrl, ...
 *
 * This service provides:
 * - listing apps for an admin
 * - creating/updating (assign) an app row keyed by slug
 * - updating logo file & storing URL
 * - updating license dates
 * - removing an app row
 */
public class AdminUserProjectService {

    private final AdminUsersRepository adminRepo;
    private final ProjectRepository projectRepo;
    private final AdminUserProjectRepository linkRepo;
    private final CurrencyRepository currencyRepository;

    public AdminUserProjectService(AdminUsersRepository adminRepo,
                                   ProjectRepository projectRepo,
                                   AdminUserProjectRepository linkRepo,
                                   CurrencyRepository currencyRepository) {
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.linkRepo = linkRepo;
        this.currencyRepository = currencyRepository;
    }

    /** List all apps (rows) for an owner (adminId). */
    @Transactional(readOnly = true)
    public List<AdminAppAssignmentResponse> list(Long adminId) {
        // Loads all AdminUserProject links for the admin, then maps each link to a response DTO.
        // Note: l.getProject() is LAZY in the entity, but inside a @Transactional(readOnly=true)
        // the persistence context is open, so accessing project fields works.
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
                        nz(l.getIpaUrl()),
                        nz(l.getBundleUrl()),
                        nz(l.getLogoUrl()),
                        // Currency is optional; if set, include code and symbol
                        l.getCurrency() != null ? l.getCurrency().getCode() : null,
                        l.getCurrency() != null ? l.getCurrency().getSymbol() : null
                ))
                .toList();
    }

    /** Get one app row (owner+project+slug). */
    @Transactional(readOnly = true)
    public AdminUserProject get(Long adminId, Long projectId, String slug) {
        // Normalizes slug (slugify) then queries for the exact row.
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

        // 1) Load the AdminUser who owns/manages the app assignment.
        AdminUser admin = adminRepo.findByAdminId(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        // 2) Load the Project to be linked.
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // 3) Build the base slug:
        //    - use req.slug if provided
        //    - otherwise derive it from req.appName
        String baseSlug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? slugify(req.getSlug())
                : slugify(req.getAppName());

        // 4) Ensure slug uniqueness within the scope (adminId, projectId).
        //    If base slug is taken, it returns base-2, base-3, ...
        String uniqueSlug = ensureUniqueSlug(admin.getAdminId(), project.getId(), baseSlug);

        // 5) Try to find an existing link row with that unique slug.
        //    If found => update; if not found => create.
        AdminUserProject link = linkRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(admin.getAdminId(), project.getId(), uniqueSlug)
                .orElse(null);

        LocalDate now = LocalDate.now();

        if (link == null) {
            // CREATE: build a new association row, with default validity dates if not provided.
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
            // UPDATE: only overwrite fields that are present in the request.
            if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
            if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
            if (req.getEndTo() != null) link.setEndTo(req.getEndTo());
        }

        // Optional updates for app metadata.
        if (req.getAppName() != null && !req.getAppName().isBlank()) {
            link.setAppName(req.getAppName());
        }
        if (req.getThemeId() != null) {
            link.setThemeId(req.getThemeId());
        }

        // 👇 NEW – set currency per app
        // If currencyCode is provided, it loads the Currency entity and assigns it to this AUP link.
        if (req.getCurrencyCode() != null && !req.getCurrencyCode().isBlank()) {
            Currency currency = currencyRepository.findByCodeIgnoreCase(req.getCurrencyCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown currency code: " + req.getCurrencyCode()));
            link.setCurrency(currency);
        }

        // If licenseId is still missing, generate a default unique-ish license identifier.
        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + admin.getAdminId() + "-" + project.getId() + "-" + now + "-" + uniqueSlug);
        }

        // Resets artifact URLs on assign (so a rebuild may be required after changes).
        // apkUrl/bundleUrl become null; ipaUrl is not reset here (kept as-is).
        link.setApkUrl(null);
        link.setBundleUrl(null);

        // Persist create/update.
        linkRepo.save(link);
    }

    /** Upload & save logo, return its public URL. */
    @Transactional
    public String updateAppLogo(Long adminId, Long projectId, String slug, MultipartFile file) throws IOException {

        // Validate input file.
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is empty");
        }

        // Ensure the link exists for this owner+project+slug.
        var link = linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

        // Build upload directory: uploads/owner/{adminId}/{projectId}/{slug}/
        Path uploadDir = Paths.get("uploads/owner", String.valueOf(adminId), String.valueOf(projectId), slugify(slug));
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        // Determine original filename and extension (default to .png if missing).
        String original = file.getOriginalFilename() == null ? "logo.png" : file.getOriginalFilename();
        String ext = original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')) : ".png";

        // Add a timestamp and UUID to avoid collisions and make filenames unique.
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safe = UUID.randomUUID() + "_" + stamp + ext;

        // Save the file, overwriting if same name exists (unlikely due to UUID+timestamp).
        Files.copy(file.getInputStream(), uploadDir.resolve(safe), StandardCopyOption.REPLACE_EXISTING);

        // Public URL used by the frontend to load the logo.
        // This assumes your Spring server serves "/uploads/**" as static content or via a controller.
        String publicUrl = "/uploads/owner/" + adminId + "/" + projectId + "/" + slugify(slug) + "/" + safe;

        // Store the URL in DB so the app assignment always points to the latest uploaded logo.
        link.setLogoUrl(publicUrl);
        linkRepo.save(link);

        return publicUrl;
    }

    /** Update only license/validity for an existing app (owner+project+slug). */
    @Transactional
    public void updateLicense(Long adminId, Long projectId, String slug, AdminAppAssignmentRequest req) {

        // Load the exact AUP link row.
        AdminUserProject link = linkRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));

        // Update only provided fields.
        if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
        if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
        if (req.getEndTo() != null) link.setEndTo(req.getEndTo());

        linkRepo.save(link);
    }

    /** Delete one app (row) under owner+project by slug. */
    @Transactional
    public void remove(Long adminId, Long projectId, String slug) {
        // If the row exists, delete it. If it doesn't exist, do nothing.
        linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .ifPresent(linkRepo::delete);
    }

    // ---------- helpers ----------

    private static String slugify(String s) {
        // Converts any string into a URL-friendly slug:
        // - lowercase
        // - replace non-alphanumeric sequences with "-"
        // - trim leading/trailing "-"
        if (s == null) return "app";
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** Ensure uniqueness per (owner, project) by appending -2, -3, ... */
    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        // If baseSlug is empty, fallback to "app".
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";

        String candidate = baseSlug;
        int i = 2;

        // Loop until we find a slug that doesn't already exist in the database for this owner+project.
        while (linkRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;

            // Safety guard to avoid infinite loops in extreme cases.
            if (i > 500) {
                throw new IllegalStateException("Could not generate a unique slug");
            }
        }
        return candidate;
    }

    // Null-safe string helper used when building response DTOs (avoid returning null strings to clients).
    private static String nz(String s) { return s == null ? "" : s; }
}
