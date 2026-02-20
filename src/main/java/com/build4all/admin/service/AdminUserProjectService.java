package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.domain.AppEnvCounter;
import com.build4all.admin.dto.AdminAppAssignmentRequest;
import com.build4all.admin.dto.AdminAppAssignmentResponse;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.admin.repository.AppEnvCounterRepository;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final CurrencyRepository currencyRepository;
    private final AppRuntimeConfigRepository runtimeRepo;
    private final AppEnvCounterRepository envCounterRepo;

    // ✅ env suffix from application.properties
    @Value("${build4all.envSuffix:test}")
    private String envSuffix;

    public AdminUserProjectService(AdminUsersRepository adminRepo,
                                   ProjectRepository projectRepo,
                                   AdminUserProjectRepository linkRepo,
                                   CurrencyRepository currencyRepository,
                                   AppRuntimeConfigRepository runtimeRepo,
                                   AppEnvCounterRepository envCounterRepo) {
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.linkRepo = linkRepo;
        this.currencyRepository = currencyRepository;
        this.runtimeRepo = runtimeRepo;
        this.envCounterRepo = envCounterRepo;
    }

    // ---------------------------
    // List / Get
    // ---------------------------

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
                        nz(l.getIpaUrl()),
                        nz(l.getBundleUrl()),
                        nz(l.getLogoUrl()),
                        l.getCurrency() != null ? l.getCurrency().getCode() : null,
                        l.getCurrency() != null ? l.getCurrency().getSymbol() : null
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserProject get(Long adminId, Long projectId, String slug) {
        return linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("App assignment not found"));
    }

    // ---------------------------
    // ✅ NEW: allocate app number per env
    // ---------------------------

    private static String normEnv(String s) {
        if (s == null || s.isBlank()) return "test";
        s = s.trim();
        if (s.startsWith(".")) s = s.substring(1);
        return s.toLowerCase();
    }

   

    @Transactional
    public int allocateAppNumberForEnv(String env) {
        final String envKey = normEnv(env);

        AppEnvCounter c = envCounterRepo.findForUpdate(envKey).orElse(null);

        if (c == null) {
            int start = linkRepo.maxAppNumberForEnv(envKey) + 1; // if none => 1
            AppEnvCounter row = new AppEnvCounter();
            row.setEnvSuffix(envKey);
            row.setNextNumber(start);

            try {
                envCounterRepo.saveAndFlush(row);
            } catch (DataIntegrityViolationException ignored) {
                // another thread created it
            }

            c = envCounterRepo.findForUpdate(envKey)
                    .orElseThrow(() -> new IllegalStateException("Cannot create/find counter for env=" + envKey));
        }

        // ✅ CRITICAL: sync counter with DB max in case old data was inserted
        int max = linkRepo.maxAppNumberForEnv(envKey);
        if (c.getNextNumber() <= max) {
            c.setNextNumber(max + 1);
        }

        int n = c.getNextNumber();
        c.setNextNumber(n + 1);
        envCounterRepo.save(c);
        return n;
    }
    // ---------------------------
    // Assign / Upsert
    // ---------------------------

    @Transactional
    public void assign(Long adminId, AdminAppAssignmentRequest req) {

       

        // 1) Load admin
        AdminUser admin = adminRepo.findByAdminId(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        // 2) Load project
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        // 3) Base slug
        String baseSlug = (req.getSlug() != null && !req.getSlug().isBlank())
                ? slugify(req.getSlug())
                : slugify(req.getAppName());

        // ✅ FIX: if slug already exists, update it (don’t auto create -2)
        AdminUserProject link = linkRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(admin.getAdminId(), project.getId(), baseSlug)
                .orElse(null);

        // 4) If not found, ensure uniqueness (create new slug if needed)
        String uniqueSlug = (link != null)
                ? baseSlug
                : ensureUniqueSlug(admin.getAdminId(), project.getId(), baseSlug);

        // if we changed slug due to uniqueness, re-load
        if (link == null) {
            link = linkRepo
                    .findByAdmin_AdminIdAndProject_IdAndSlug(admin.getAdminId(), project.getId(), uniqueSlug)
                    .orElse(null);
        }

    

        final String currentEnv = normEnv(this.envSuffix); // ✅ final + normalized

        LocalDate now = LocalDate.now();

        if (link == null) {
            link = new AdminUserProject(
                    admin,
                    project,
                    req.getLicenseId(),
                    req.getValidFrom() != null ? req.getValidFrom() : now,
                    req.getEndTo() != null ? req.getEndTo() : now.plusMonths(1)
            );

            link.setStatus("TEST");
            link.setSlug(uniqueSlug);

            // ✅ NEW app => allocate numbers + ids ONCE
            link.setEnvSuffix(currentEnv);
            link.setAppNumber(allocateAppNumberForEnv(currentEnv));

            link.ensureAndroidPackageName();
            link.ensureIosBundleId();

        } else {
            // update existing (NO env/appNumber regeneration)
            if (req.getLicenseId() != null) link.setLicenseId(req.getLicenseId());
            if (req.getValidFrom() != null) link.setValidFrom(req.getValidFrom());
            if (req.getEndTo() != null) link.setEndTo(req.getEndTo());

            // ✅ Backfill only if old row had nulls (from old data)
            if (link.getEnvSuffix() == null || link.getEnvSuffix().isBlank()) {
                link.setEnvSuffix(currentEnv);
            }
            if (link.getAppNumber() == null) {
                link.setAppNumber(allocateAppNumberForEnv(link.getEnvSuffix()));
            }

            if (link.getAndroidPackageName() == null || link.getAndroidPackageName().isBlank()) {
                link.ensureAndroidPackageName();
            }
            if (link.getIosBundleId() == null || link.getIosBundleId().isBlank()) {
                link.ensureIosBundleId();
            }
        }

        // App metadata
        if (req.getAppName() != null && !req.getAppName().isBlank()) {
            link.setAppName(req.getAppName());
        }
        if (req.getThemeId() != null) {
            link.setThemeId(req.getThemeId());
        }

        // Currency per app
        if (req.getCurrencyCode() != null && !req.getCurrencyCode().isBlank()) {
            Currency currency = currencyRepository.findByCodeIgnoreCase(req.getCurrencyCode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown currency code: " + req.getCurrencyCode()));
            link.setCurrency(currency);
        }

        // License fallback
        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + admin.getAdminId() + "-" + project.getId() + "-" + now + "-" + uniqueSlug);
        }

        // Reset build artifacts on assign
        link.setApkUrl(null);
        link.setBundleUrl(null);

        // ✅ Save AUP
        AdminUserProject savedLink = linkRepo.save(link);

        // ✅ Upsert runtime config
        AppRuntimeConfig cfg = runtimeRepo.findByApp_Id(savedLink.getId()).orElseGet(() -> {
            AppRuntimeConfig c = new AppRuntimeConfig();
            c.setApp(savedLink);
            return c;
        });

        if (req.getNavJson() != null) cfg.setNavJson(req.getNavJson());
        if (req.getHomeJson() != null) cfg.setHomeJson(req.getHomeJson());
        if (req.getEnabledFeaturesJson() != null) cfg.setEnabledFeaturesJson(req.getEnabledFeaturesJson());
        if (req.getBrandingJson() != null) cfg.setBrandingJson(req.getBrandingJson());
        if (req.getApiBaseUrlOverride() != null) cfg.setApiBaseUrlOverride(req.getApiBaseUrlOverride());

        runtimeRepo.save(cfg);
    }

    // ---------------------------
    // Logo / License / Remove
    // ---------------------------

    @Transactional
    public String updateAppLogo(Long adminId, Long projectId, String slug, MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is empty");
        }

        AdminUserProject link = linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
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

    @Transactional
    public void remove(Long adminId, Long projectId, String slug) {
        linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .ifPresent(linkRepo::delete);
    }

    // ---------------------------
    // helpers
    // ---------------------------

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";

        String candidate = baseSlug;
        int i = 2;

        while (linkRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "-" + i;
            i++;
            if (i > 500) throw new IllegalStateException("Could not generate a unique slug");
        }
        return candidate;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}