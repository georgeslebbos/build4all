package com.build4all.app.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.app.domain.AppRequest;
import com.build4all.app.domain.AppRuntimeConfig;
import com.build4all.app.dto.CiDispatchResult;
import com.build4all.app.repository.AppRequestRepository;
import com.build4all.app.repository.AppRuntimeConfigRepository;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.repository.CurrencyRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final CurrencyRepository currencyRepo;
    private final AppRuntimeConfigRepository runtimeRepo;

    public AppRequestService(AppRequestRepository appRequestRepo,
                             AdminUserProjectRepository aupRepo,
                             AdminUsersRepository adminRepo,
                             ProjectRepository projectRepo,
                             ThemeRepository themeRepo,
                             CiBuildService ciBuildService,
                             CurrencyRepository currencyRepo,
                             AppRuntimeConfigRepository runtimeRepo) {
        this.appRequestRepo = appRequestRepo;
        this.aupRepo = aupRepo;
        this.adminRepo = adminRepo;
        this.projectRepo = projectRepo;
        this.themeRepo = themeRepo;
        this.ciBuildService = ciBuildService;
        this.currencyRepo = currencyRepo;
        this.runtimeRepo = runtimeRepo;
    }

    /** Legacy JSON create (kept). */
    public AppRequest createRequest(Long ownerId, Long projectId,
                                    String appName, String slug,
                                    String logoUrl, Long themeId,
                                    String themeJson,
                                    String notes) {
        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(slugifyOrFallback(slug, appName));
        r.setLogoUrl(logoUrl);
        r.setThemeId(themeId);
        r.setThemeJson(themeJson);
        r.setNotes(notes);
        return appRequestRepo.save(r);
    }

    @Transactional
    public AdminUserProject createAndAutoApprove(Long ownerId, Long projectId,
                                                 String appName, String slug,
                                                 MultipartFile logoFile,
                                                 Long themeId, String notes,
                                                 String themeJson,
                                                 Long currencyId,
                                                 String navJson,
                                                 String homeJson,
                                                 String enabledFeaturesJson,
                                                 String brandingJson,
                                                 String apiBaseUrlOverride
    ) throws IOException {

        String base = (slug != null && !slug.isBlank()) ? slugify(slug) : slugify(appName);
        String uniqueSlug = ensureUniqueSlug(ownerId, projectId, base);

        String logoUrl = null;
        byte[] logoBytes = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            logoUrl = saveOwnerAppLogoToUploads(ownerId, projectId, uniqueSlug, logoFile);
            logoBytes = logoFile.getBytes();
        }

        Long chosenThemeId = resolveThemeIdFromRaw(themeId);

        AppRequest r = new AppRequest();
        r.setOwnerId(ownerId);
        r.setProjectId(projectId);
        r.setAppName(appName);
        r.setSlug(uniqueSlug);
        r.setLogoUrl(logoUrl);
        r.setThemeId(chosenThemeId);
        r.setNotes(notes);
        r.setStatus("APPROVED");
        r.setThemeJson(themeJson);
        r.setCurrencyId(currencyId);
        r = appRequestRepo.save(r);

        return provisionAndTrigger(
                r,
                logoBytes,
                navJson,
                homeJson,
                enabledFeaturesJson,
                brandingJson,
                apiBaseUrlOverride
        );
    }

    // ---------------- internal ----------------

    private AdminUserProject provisionAndTrigger(AppRequest req,
                                                 byte[] logoBytesOpt,
                                                 String navJson,
                                                 String homeJson,
                                                 String enabledFeaturesJson,
                                                 String brandingJson,
                                                 String apiBaseUrlOverride
    ) {
        AdminUser owner = adminRepo.findById(req.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Owner(admin) not found"));

        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Long chosenThemeId = resolveThemeId(req);

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

        Long currencyId = req.getCurrencyId();
        if (currencyId != null) {
            Currency c = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + currencyId));
            link.setCurrency(c);
        }

        if (link.getLicenseId() == null || link.getLicenseId().isBlank()) {
            link.setLicenseId("LIC-" + owner.getAdminId() + "-" + project.getId() + "-" + now + "-" + uniqueSlug);
        }

        // bump version each time a build is requested
        link.bumpAndroidVersion();

        // reset build artifacts
        link.setApkUrl(null);
        link.setIpaUrl(null);
        link.setBundleUrl(null);

        // ✅ save -> ensure package (needs ID) -> save again
        link = aupRepo.save(link);
        link.ensureAndroidPackageName();
        link = aupRepo.save(link);

        // Save runtime JSON configs if provided
        upsertRuntimeConfig(link, navJson, homeJson, enabledFeaturesJson, brandingJson, apiBaseUrlOverride);

        String ownerProjectLinkId = String.valueOf(link.getId());
        Long currencyIdForBuild = (link.getCurrency() != null) ? link.getCurrency().getId() : null;

        String themeJson = (req.getThemeJson() != null && !req.getThemeJson().isBlank())
                ? req.getThemeJson()
                : resolveThemeJson(chosenThemeId, "{}");

        String appType = (project.getProjectType() != null)
                ? project.getProjectType().name()
                : "ECOMMERCE";

        String androidPackageName = link.getAndroidPackageName();

        CiDispatchResult res = ciBuildService.dispatchOwnerAndroidBuild(
                owner.getAdminId(),
                project.getId(),
                ownerProjectLinkId,
                uniqueSlug,
                req.getAppName(),
                appType,
                chosenThemeId,
                req.getLogoUrl(),
                themeJson,
                logoBytesOpt,
                currencyIdForBuild,
                apiBaseUrlOverride,
                navJson,
                homeJson,
                enabledFeaturesJson,
                brandingJson,
                link.getAndroidVersionCode(),
                link.getAndroidVersionName(),
                androidPackageName
        );

        if (!res.ok()) {
            String msg = "CI dispatch failed (HTTP " + res.httpCode() + "): " + res.responseBody();
            log.error("{}", msg);
            throw new IllegalStateException(msg);
        }

        log.info("CI dispatch OK (buildId={}, ownerId={}, projectId={}, linkId={}, slug={})",
                res.buildId(), owner.getAdminId(), project.getId(), link.getId(), uniqueSlug);

        return link;
    }

    private void upsertRuntimeConfig(AdminUserProject link,
                                     String navJson,
                                     String homeJson,
                                     String enabledFeaturesJson,
                                     String brandingJson,
                                     String apiBaseUrlOverride) {

        boolean hasAny =
                (navJson != null && !navJson.isBlank()) ||
                        (homeJson != null && !homeJson.isBlank()) ||
                        (enabledFeaturesJson != null && !enabledFeaturesJson.isBlank()) ||
                        (brandingJson != null && !brandingJson.isBlank()) ||
                        (apiBaseUrlOverride != null && !apiBaseUrlOverride.isBlank());

        if (!hasAny) return;

        AppRuntimeConfig cfg = runtimeRepo.findByApp_Id(link.getId()).orElseGet(() -> {
            AppRuntimeConfig c = new AppRuntimeConfig();
            c.setApp(link);
            return c;
        });

        if (navJson != null) cfg.setNavJson(navJson);
        if (homeJson != null) cfg.setHomeJson(homeJson);
        if (enabledFeaturesJson != null) cfg.setEnabledFeaturesJson(enabledFeaturesJson);
        if (brandingJson != null) cfg.setBrandingJson(brandingJson);
        if (apiBaseUrlOverride != null) cfg.setApiBaseUrlOverride(apiBaseUrlOverride);

        runtimeRepo.save(cfg);
    }

    private Long resolveThemeId(AppRequest req) {
        Long requested = req.getThemeId();
        if (requested != null) {
            return themeRepo.findById(requested)
                    .map(Theme::getId)
                    .orElseGet(() -> themeRepo.findByIsActiveTrue().map(Theme::getId).orElse(null));
        }

        String json = req.getThemeJson();
        if (json != null && !json.isBlank()) {
            Theme t = new Theme();
            String themeName = (req.getAppName() != null && !req.getAppName().isBlank())
                    ? req.getAppName().trim()
                    : ("ReqTheme-" + req.getId());
            t.setName(themeName);
            t.setThemeJson(json);
            t.setIsActive(false);
            t = themeRepo.save(t);

            req.setThemeId(t.getId());
            appRequestRepo.save(req);

            return t.getId();
        }

        return themeRepo.findByIsActiveTrue()
                .map(Theme::getId)
                .orElse(null);
    }

    private String resolveThemeJson(Long themeId, String fallbackJson) {
        if (themeId != null) {
            return themeRepo.findById(themeId)
                    .map(Theme::getThemeJson)
                    .orElseGet(() -> (fallbackJson != null && !fallbackJson.isBlank()) ? fallbackJson : "{}");
        }
        if (fallbackJson != null && !fallbackJson.isBlank()) {
            return fallbackJson;
        }
        return themeRepo.findByIsActiveTrue()
                .map(Theme::getThemeJson)
                .orElse("{}");
    }

    private Long resolveThemeIdFromRaw(Long requested) {
        if (requested != null) {
            return themeRepo.findById(requested)
                    .map(Theme::getId)
                    .orElseGet(() ->
                            themeRepo.findByIsActiveTrue()
                                    .map(Theme::getId)
                                    .orElse(null));
        }
        return themeRepo.findByIsActiveTrue()
                .map(Theme::getId)
                .orElse(null);
    }

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

    // ---------------- RESTORED METHODS (needed by CiCallbackController) ----------------

    /** Save APK URL by (ownerId, projectId, slug). */
    @Transactional
    public AdminUserProject setApkUrl(Long adminId, Long projectId, String slug, String apkUrl) {
        AdminUserProject link = aupRepo
                .findByAdmin_AdminIdAndProject_IdAndSlug(adminId, projectId, slugify(slug))
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject app not found"));
        link.setApkUrl(apkUrl);
        return aupRepo.save(link);
    }

    /** Save APK URL by AdminUserProject primary key (CI callback). */
    @Transactional
    public void setApkUrlByLinkId(Long linkId, String apkUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setApkUrl(apkUrl);
        aupRepo.save(link);
    }

    /** Save AAB URL by AdminUserProject primary key. */
    @Transactional
    public void setBundleUrlByLinkId(Long linkId, String bundleUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setBundleUrl(bundleUrl);
        aupRepo.save(link);
    }

    /** Save IPA URL by AdminUserProject primary key. */
    @Transactional
    public void setIpaUrlByLinkId(Long linkId, String ipaUrl) {
        AdminUserProject link = aupRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("OwnerProject link not found: " + linkId));
        link.setIpaUrl(ipaUrl);
        aupRepo.save(link);
    }

    // ---------------- helpers ----------------

    private static String slugifyOrFallback(String maybeSlug, String fallbackName) {
        if (maybeSlug != null && !maybeSlug.isBlank()) return slugify(maybeSlug);
        return slugify(fallbackName);
    }

    private String ensureUniqueSlug(Long ownerId, Long projectId, String baseSlug) {
        if (baseSlug == null || baseSlug.isBlank()) baseSlug = "app";
        String candidate = baseSlug;
        int i = 2;
        while (aupRepo.existsByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, candidate)) {
            candidate = baseSlug + "_" + i;
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
}
